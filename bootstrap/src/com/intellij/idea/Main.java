package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Main {
  private static boolean isHeadless;

  private Main() {
  }

  public static void main(final String[] args) {
    installPatch();

    isHeadless = isHeadless(args);
    if (isHeadless) {
      System.setProperty("java.awt.headless", Boolean.TRUE.toString());
    } else if (GraphicsEnvironment.isHeadless()) {
      throw new HeadlessException("Unable to detect graphics environment");
    }

    Bootstrap.main(args, Main.class.getName() + "Impl", "start");
  }

  public static boolean isHeadless(final String[] args) {
    @NonNls final String inspectAppCode = "inspect";
    @NonNls final String antAppCode = "ant";
    @NonNls final String duplocateCode = "duplocate";
    @NonNls final String traverseUI = "traverseUI";
    return args.length > 0 && (Comparing.strEqual(args[0], inspectAppCode) ||
                               Comparing.strEqual(args[0], antAppCode) ||
                               Comparing.strEqual(args[0], duplocateCode) ||
                               Comparing.strEqual(args[0], traverseUI));
  }

  public static boolean isCommandLine(final String[] args) {
    if (isHeadless(args)) return true;
    @NonNls final String diffAppCode = "diff";
    return args.length > 0 && Comparing.strEqual(args[0], diffAppCode);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  private static void installPatch() {
    try {
      File ideaHomeDir = getIdeaHomeDir();
      if (ideaHomeDir == null) return;

      File patchFile = new File(ideaHomeDir, "patch.jar");
      if (!patchFile.exists()) return;

      File tempFile = File.createTempFile("idea.patch", null);
      tempFile.deleteOnExit();
      copyFile(patchFile, tempFile);
      patchFile.delete();

      Process process = Runtime.getRuntime().exec(new String[]{
        System.getProperty("java.home") + "/bin/java",
        "-classpath",
        tempFile.getPath(),
        "com.intellij.updater.Runner",
        "install",
        ideaHomeDir.getPath()});

      Thread outThread = new Thread(new StreamRedirector(process.getInputStream(), System.out));
      Thread errThread = new Thread(new StreamRedirector(process.getErrorStream(), System.err));
      outThread.start();
      errThread.start();

      try {
        process.waitFor();
      }
      finally {
        outThread.join();
        errThread.join();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static class StreamRedirector implements Runnable {
    private InputStream myIn;
    private OutputStream myOut;

    private StreamRedirector(InputStream in, OutputStream out) {
      myIn = in;
      myOut = out;
    }

    public void run() {
      try {
        copyStream(myIn, myOut);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static File getIdeaHomeDir() throws IOException {
    URL url = Bootstrap.class.getResource("");
    if (url == null || !"jar".equals(url.getProtocol())) return null;

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) return null;

    String jarFileUrl = path.substring(start, end);

    try {
      File bootstrapJar = new File(new URI(jarFileUrl));
      return bootstrapJar.getParentFile().getParentFile();
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  public static void copyFile(File from, File to) throws IOException {
    to.getParentFile().mkdirs();

    FileInputStream is = null;
    FileOutputStream os = null;
    try {
      is = new FileInputStream(from);
      os = new FileOutputStream(to);

      copyStream(is, os);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (os != null) {
        try {
          os.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void copyStream(InputStream from, OutputStream to) throws IOException {
    byte[] buffer = new byte[65536];
    while (true) {
      int read = from.read(buffer);
      if (read < 0) break;
      to.write(buffer, 0, read);
    }
  }
}
