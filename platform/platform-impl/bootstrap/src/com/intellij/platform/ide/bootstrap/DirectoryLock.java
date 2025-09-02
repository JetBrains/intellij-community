// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.CliResult;
import com.intellij.ide.SpecialConfigFiles;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.LoggerFactory;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.ui.User32Ex;
import com.intellij.util.Suppressions;
import com.intellij.util.TimeoutUtil;
import com.sun.jna.platform.win32.WinDef;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.Objects.requireNonNullElse;

/**
 * The class ensures that only one IDE instance is running on the given pair of configuration/cache directories
 * and participates in the CLI bypassing arguments and relaying back exit codes and error messages.
 */
@ApiStatus.Internal
public final class DirectoryLock {
  @ApiStatus.Internal
  public static final class CannotActivateException extends Exception implements ExceptionWithAttachments {
    private final @Nls String myMessage;
    private final Attachment[] myAttachments;

    private CannotActivateException(@Nls String message, String diagnostic, String threadDump) {
      myMessage = message;
      myAttachments = new Attachment[]{
        new Attachment("diagnostic.txt", diagnostic),
        new Attachment("threadDump.txt", threadDump)
      };
    }

    @Override
    public @Nls String getMessage() {
      return myMessage;
    }

    @Override
    public @NotNull Attachment @NotNull [] getAttachments() {
      return myAttachments;
    }
  }

  private static final int UDS_PATH_LENGTH_LIMIT = 100;
  private static final int BUFFER_LENGTH = 16_384;
  private static final int MARKER = 0xFACADE;
  private static final int HEADER_LENGTH = 6;  // the marker (4 bytes) + a packet length (2 bytes)
  private static final String SERVER_THREAD_NAME = "External Command Listener";

  private static final Logger LOG = getLogger();
  private static final AtomicInteger COUNT = new AtomicInteger();  // to ensure redirected port file uniqueness in tests
  private static final long TIMEOUT_MS = Integer.getInteger("ij.dir.lock.timeout", 5_000);
  private static final List<String> ACK_PACKET = List.of("<<ACK>>");

  private static Logger getLogger() {
    var logger = Logger.getInstance(DirectoryLock.class);
    if (Boolean.getBoolean("ij.dir.lock.debug")) logger.setLevel(LogLevel.DEBUG);
    return logger;
  }

  private final String myPid = Long.toString(ProcessHandle.current().pid());
  private final Path myPortFile;
  private final Path myLockFile;
  private final boolean myFallbackMode;
  private final @Nullable Path myRedirectedPortFile;
  private final Function<List<String>, CliResult> myProcessor;

  private volatile @Nullable ServerSocketChannel myServerChannel = null;

  public DirectoryLock(@NotNull Path configPath, @NotNull Path systemPath, @NotNull Function<List<String>, CliResult> processor) {
    myPortFile = systemPath.resolve(SpecialConfigFiles.PORT_FILE);
    myLockFile = configPath.resolve(SpecialConfigFiles.LOCK_FILE);

    myFallbackMode = !areUdsSupported(myPortFile);

    if (LOG.isDebugEnabled()) {
      LOG.debug("portFile=" + myPortFile + " lockFile=" + myLockFile + " fallback=" + myFallbackMode + " PID=" + myPid);
    }

    if (!myFallbackMode && myPortFile.toString().length() > UDS_PATH_LENGTH_LIMIT) {
      var baseDir = SystemInfoRt.isWindows ? Path.of(System.getenv("SystemRoot"), "Temp") : Path.of("/tmp");
      myRedirectedPortFile = baseDir.resolve(".ij_redirected_port_" + myPid + "_" + COUNT.incrementAndGet());
      if (LOG.isDebugEnabled()) LOG.debug("redirectedPortFile=" + myRedirectedPortFile);
    }
    else {
      myRedirectedPortFile = null;
    }

    myProcessor = processor;
  }

  private static boolean areUdsSupported(Path file) {
    var fs = file.getFileSystem();
    if (fs.getClass().getModule() != Object.class.getModule()) {
      if (!System.getProperty("java.vm.vendor", "").contains("JetBrains")) {
        return false;
      }
      try {
        fs.provider().getClass().getMethod("getSunPathForSocketFile", Path.class);
      }
      catch (NoSuchMethodException | SecurityException e) {
        return false;
      }
    }

    if (!SystemInfoRt.isUnix) {
      try {
        SocketChannel.open(StandardProtocolFamily.UNIX).close();
      }
      catch (UnsupportedOperationException e) {
        return false;
      }
      catch (IOException ignored) { }
    }

    return true;
  }

  /**
   * Tries to grab a port file (thus locking the directories) and start listening for incoming requests.
   * Failing that, attempts to connect via the existing port file to an already running instance.
   * Returns {@code null} on successfully locking the directories, a non-null value on successfully activating another instance,
   * or throws a {@link CannotActivateException}.
   */
  public @Nullable CliResult lockOrActivate(@NotNull Path currentDirectory, @NotNull List<String> args) throws CannotActivateException, IOException {
    var configDir = NioFiles.createDirectories(myLockFile.getParent());
    var systemDir = NioFiles.createDirectories(myPortFile.getParent());
    if (Files.isSameFile(systemDir, configDir)) {
      throw new IllegalArgumentException(BootstrapBundle.message("bootstrap.error.same.directories"));
    }

    var suppressed = new ArrayList<Exception>();
    var command = ProcessHandle.current().info().command().orElse("???");

    for (int attempt = 0; attempt < 1; attempt++) {
      try {
        return tryListen();
      }
      catch (IOException e) {
        LOG.debug(e);
        suppressed.add(e);
      }

      try {
        return tryConnect(args, currentDirectory);
      }
      catch (IOException e) {
        LOG.debug(e);
        suppressed.add(e);
      }

      try {
        var otherPid = remotePID();
        var handle = ProcessHandle.of(otherPid).orElse(null);
        if (handle != null && command.equals(handle.info().command().orElse(""))) {
          cannotActivate(command, otherPid, suppressed);
        }
      }
      catch (IOException | NumberFormatException e) {
        LOG.debug(e);
        suppressed.add(e);
      }

      LOG.debug("retrying in 200 ms ...");
      TimeoutUtil.sleep(200);
    }

    if (!Path.of(command).endsWith(SystemInfoRt.isWindows ? "java.exe" : "java")) {
      var user = ProcessHandle.current().info().user().orElse("");
      var other = ProcessHandle.allProcesses()
        .filter(ph -> command.equals(ph.info().command().orElse("")) && user.equals(ph.info().user().orElse("")) && ph.pid() != ProcessHandle.current().pid())
        .findFirst().orElse(null);
      if (other != null) {
        cannotActivate(command, other.pid(), suppressed);
      }
    }

    try {
      if (LOG.isDebugEnabled()) LOG.debug("deleting " + myPortFile);
      Files.deleteIfExists(myPortFile);
      if (myRedirectedPortFile != null) {
        if (LOG.isDebugEnabled()) LOG.debug("deleting " + myRedirectedPortFile);
        Files.deleteIfExists(myRedirectedPortFile);
      }
      if (LOG.isDebugEnabled()) LOG.debug("deleting " + myLockFile);
      Files.deleteIfExists(myLockFile);

      return tryListen();
    }
    catch (IOException e) {
      suppressed.forEach(e::addSuppressed);
      throw e;
    }
  }

  private void cannotActivate(String command, long pid, List<Exception> suppressed) throws CannotActivateException {
    var diagnostic = diagnostic();
    var threadDump = remoteThreadDump(pid);
    var cae = new CannotActivateException(BootstrapBundle.message("bootstrap.error.still.running", command, pid), diagnostic, threadDump);
    suppressed.forEach(cae::addSuppressed);
    throw cae;
  }

  @VisibleForTesting
  public void dispose() {
    dispose(true);
  }

  private void dispose(boolean deleteLockFile) {
    var serverChannel = myServerChannel;
    myServerChannel = null;
    if (serverChannel != null) {
      if (LOG.isDebugEnabled()) LOG.debug("cleaning up");
      Suppressions.runSuppressing(
        serverChannel::close,
        () -> {
          if (myRedirectedPortFile != null) {
            Files.deleteIfExists(myRedirectedPortFile);
          }
        },
        () -> Files.deleteIfExists(myPortFile),
        () -> {
          if (deleteLockFile) {
            Files.deleteIfExists(myLockFile);
          }
        });
    }
  }

  private @Nullable CliResult tryListen() throws IOException {
    var serverChannel = ServerSocketChannel.open(myFallbackMode ? StandardProtocolFamily.INET : StandardProtocolFamily.UNIX);

    SocketAddress address;
    if (myFallbackMode) {
      Files.writeString(myPortFile, "0", StandardOpenOption.CREATE_NEW);
      address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0);
    }
    else if (myRedirectedPortFile != null) {
      Files.writeString(myPortFile, myRedirectedPortFile.toString(), StandardOpenOption.CREATE_NEW);
      address = UnixDomainSocketAddress.of(myRedirectedPortFile);
    }
    else {
      address = UnixDomainSocketAddress.of(myPortFile);
    }

    if (LOG.isDebugEnabled()) LOG.debug("binding to " + address);
    serverChannel.bind(address);
    myServerChannel = serverChannel;

    try {
      if (myFallbackMode) {
        var port = ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();
        Files.writeString(myPortFile, String.valueOf(port), StandardOpenOption.TRUNCATE_EXISTING);
      }

      Files.writeString(myLockFile, myPid, StandardOpenOption.CREATE_NEW);
    }
    catch (IOException e) {
      LOG.debug(e);
      dispose(false);
      throw new IOException("Cannot lock config directory " + myLockFile.getParent(), e);
    }

    new Thread(this::acceptConnections, SERVER_THREAD_NAME).start();
    return null;
  }

  private CliResult tryConnect(List<String> args, Path currentDirectory) throws IOException {
    var pf = myFallbackMode ? StandardProtocolFamily.INET : StandardProtocolFamily.UNIX;
    try (var socketChannel = SocketChannel.open(pf); var selector = Selector.open()) {
      socketChannel.configureBlocking(false);

      SocketAddress address;
      if (myFallbackMode) {
        var port = 0;
        try { port = Integer.parseInt(Files.readString(myPortFile)); }
        catch (NumberFormatException e) { throw new SocketException("Invalid port; " + e.getMessage()); }
        address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port);
      }
      else if (myRedirectedPortFile != null) {
        address = UnixDomainSocketAddress.of(Files.readString(myPortFile));
      }
      else {
        address = UnixDomainSocketAddress.of(myPortFile);
      }

      if (LOG.isDebugEnabled()) LOG.debug("connecting to " + address);
      socketChannel.register(selector, SelectionKey.OP_CONNECT);
      if (!socketChannel.connect(address)) {
        if (selector.select(TIMEOUT_MS) == 0) throw new SocketTimeoutException("Timeout: connect");
        socketChannel.finishConnect();
      }
      LOG.debug("... connected");
      socketChannel.register(selector, SelectionKey.OP_READ);

      allowActivation();

      var request = new ArrayList<String>(args.size() + 1);
      request.add(currentDirectory.toString());
      request.addAll(args);
      sendLines(socketChannel, request);

      try {
        if (selector.select(TIMEOUT_MS) == 0) throw new SocketTimeoutException("Timeout: ACK");
        var ack = receiveLines(socketChannel);
        if (!ack.equals(ACK_PACKET)) throw new IOException("Malformed ACK: " + ack);

        var response = receiveLines(socketChannel);
        if (response.size() != 2) throw new IOException("Malformed response: " + response);
        var exitCode = Integer.parseInt(response.get(0));
        var message = response.get(1);
        return new CliResult(exitCode, message.isEmpty() ? null : message);
      }
      catch (EOFException e) {
        LOG.debug(e);
        throw new IOException("Communication interrupted", e);
      }
    }
  }

  private void allowActivation() {
    if (SystemInfoRt.isWindows && JnaLoader.isLoaded()) {
      try {
        User32Ex.INSTANCE.AllowSetForegroundWindow(new WinDef.DWORD(remotePID()));
      }
      catch (Throwable t) {
        LOG.debug(t);
      }
    }
  }

  private void acceptConnections() {
    var channel = (ServerSocketChannel)null;
    while ((channel = myServerChannel) != null) {
      try {
        if (LOG.isDebugEnabled()) LOG.debug("accepting connections at " + channel.getLocalAddress());
        var socketChannel = channel.accept();
        if (LOG.isDebugEnabled()) LOG.debug("accepted connection " + socketChannel);
        ProcessIOExecutorService.INSTANCE.execute(() -> handleConnection(socketChannel));
      }
      catch (ClosedChannelException e) {
        LOG.debug(e);
        break;
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }
  }

  private void handleConnection(SocketChannel socketChannel) {
    try (socketChannel) {
      var request = receiveLines(socketChannel);

      sendLines(socketChannel, ACK_PACKET);

      CliResult result;
      try {
        result = myProcessor.apply(request);
      }
      catch (Throwable t) {
        LOG.error(t);
        var error = requireNonNullElse(t.getMessage(), "Unknown error");
        var message = BootstrapBundle.message("bootstrap.error.request.failed", myPid, t.getClass(), error, LoggerFactory.getLogFilePath());
        result = new CliResult(AppExitCodes.ACTIVATE_ERROR, message);
      }

      sendLines(socketChannel, List.of(String.valueOf(result.exitCode()), requireNonNullElse(result.message(), "")));
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  //<editor-fold desc="Helpers">
  @VisibleForTesting
  public @Nullable Path getRedirectedPortFile() {
    return myRedirectedPortFile;
  }

  private long remotePID() throws IOException {
    return Long.parseLong(Files.readString(myLockFile));
  }

  @SuppressWarnings("StringBufferReplaceableByString")
  private String diagnostic() {
    var sb = new StringBuilder();
    sb.append("Timestamp: ").append(LocalDateTime.now()).append('\n');
    sb.append("Port file: ").append(myPortFile).append('\n');
    sb.append("Redirected: ").append(myRedirectedPortFile).append('\n');
    sb.append("Fallback: ").append(myFallbackMode).append('\n');
    sb.append("Lock file: ").append(myLockFile).append('\n');
    return sb.toString();
  }

  private static String remoteThreadDump(long pid) {
    try {
      var vm = VirtualMachine.attach(String.valueOf(pid));
      try {
        var args = new Object[]{new Object[]{""}};
        var is = (InputStream)vm.getClass().getMethod("remoteDataDump", Object[].class).invoke(vm, args);
        var out = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
          var line = "";
          while ((line = reader.readLine()) != null) {
            out.append(line).append("\n");
          }
        }
        return out.toString();
      }
      finally {
        vm.detach();
      }
    }
    catch (Exception e) {
      return "Cannot collect thread dump: " + e.getClass().getName() + ": " + e.getMessage();
    }
  }

  private static void sendLines(SocketChannel socketChannel, List<String> lines) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_LENGTH);
    buffer.putInt(MARKER).putShort((short)0);

    for (var line : lines) {
      var bytes = line.getBytes(StandardCharsets.UTF_8);
      buffer.putShort((short)bytes.length);
      buffer.put(bytes);
    }

    buffer.putShort(4, (short)buffer.position());

    if (LOG.isDebugEnabled()) LOG.debug("sending: " + lines + ", bytes:" + buffer.position());
    buffer.flip();
    while (buffer.hasRemaining()) {
      socketChannel.write(buffer);
    }
  }

  private static List<String> receiveLines(SocketChannel socketChannel) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_LENGTH);

    buffer.limit(HEADER_LENGTH);
    while (buffer.position() < HEADER_LENGTH) {
      if (socketChannel.read(buffer) < 0) {
        throw new EOFException("Expected " + HEADER_LENGTH + " bytes, got " + buffer.position());
      }
    }
    var marker = buffer.getInt(0);
    if (marker != MARKER) throw new StreamCorruptedException("Invalid marker: 0x" + Integer.toHexString(marker));
    var length = buffer.getShort(4);
    if (LOG.isDebugEnabled()) LOG.debug("receiving: " + length + " bytes");
    buffer.limit(length);
    while (buffer.position() < length) {
      if (socketChannel.read(buffer) < 0) {
        throw new EOFException("Expected " + length + " bytes, got " + buffer.position());
      }
    }

    buffer.position(HEADER_LENGTH);
    var lines = new ArrayList<String>();
    while (buffer.hasRemaining()) {
      var lineLength = buffer.getShort();
      if (lineLength > 0) {
        var bytes = new byte[lineLength];
        buffer.get(bytes);
        lines.add(new String(bytes, StandardCharsets.UTF_8));
      }
      else {
        lines.add("");
      }
    }
    return lines;
  }
  //</editor-fold>
}
