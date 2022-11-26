// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
@ApiStatus.Internal
public final class BuildDependenciesUtil {
  private static final Logger LOG = Logger.getLogger(BuildDependenciesUtil.class.getName());

  private static final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
  private static final int octal_0111 = Integer.parseInt("111", 8);
  private static final boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows");

  @SuppressWarnings("HttpUrlsUsage")
  private static DocumentBuilder createDocumentBuilder() {
    // from https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
    try {
      String FEATURE;

      // This is the PRIMARY defense. If DTDs (doctype) are disallowed, almost all
      // XML entity attacks are prevented
      // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
      FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
      dbf.setFeature(FEATURE, true);

      // If you can't completely disable DTDs, then at least do the following:
      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
      // JDK7+ - http://xml.org/sax/features/external-general-entities
      //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-general-entities";
      dbf.setFeature(FEATURE, false);

      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
      // JDK7+ - http://xml.org/sax/features/external-parameter-entities
      //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-parameter-entities";
      dbf.setFeature(FEATURE, false);

      // Disable external DTDs as well
      FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
      dbf.setFeature(FEATURE, false);

      // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);

      // And, per Timothy Morgan: "If for some reason support for inline DOCTYPE is a requirement, then
      // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
      // (http://cwe.mitre.org/data/definitions/918.html) and denial
      // of service attacks (such as a billion laughs or decompression bombs via "jar:") are a risk."

      return dbf.newDocumentBuilder();
    }
    catch (Throwable throwable) {
      throw new IllegalStateException("Unable to create DOM parser", throwable);
    }
  }

  private static Element getSingleChildElement(Element parent, String tagName) {
    NodeList childNodes = parent.getChildNodes();

    ArrayList<Element> result = new ArrayList<>();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node node = childNodes.item(i);
      if (node instanceof Element) {
        Element element = (Element)node;
        if (tagName.equals(element.getTagName())) {
          result.add(element);
        }
      }
    }

    if (result.size() != 1) {
      throw new IllegalStateException("Expected one and only one element by tag '" + tagName + "'");
    }

    return result.get(0);
  }

  public static String getLibraryMavenId(Path libraryXml) {
    try {
      DocumentBuilder documentBuilder = createDocumentBuilder();
      Document document = documentBuilder.parse(libraryXml.toFile());

      Element libraryElement = getSingleChildElement(document.getDocumentElement(), "library");
      Element propertiesElement = getSingleChildElement(libraryElement, "properties");
      String mavenId = propertiesElement.getAttribute("maven-id");
      if (mavenId.isBlank()) {
        throw new IllegalStateException("Invalid maven-id");
      }

      return mavenId;
    }
    catch (Throwable t) {
      throw new IllegalStateException("Unable to load maven-id from " + libraryXml + ": " + t.getMessage(), t);
    }
  }

  public static void extractZip(Path archiveFile, Path target, boolean stripRoot) throws Exception {
    try (ZipFile zipFile = new ZipFile(FileChannel.open(archiveFile))) {
      Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
      genericExtract(archiveFile, new ArchiveContent() {
        @Override
        public @Nullable Entry getNextEntry() {
          if (!entries.hasMoreElements()) {
            return null;
          }

          ZipArchiveEntry entry = entries.nextElement();
          if (entry == null) {
            return null;
          }

          return new Entry() {
            @Override
            public @NotNull Type getType() {
              if (entry.isUnixSymlink()) {
                return Type.SYMLINK;
              }

              return entry.isDirectory() ? Type.DIR : Type.FILE;
            }

            @Override
            public @NotNull String getName() {
              return entry.getName();
            }

            @Override
            public boolean isExecutable() {
              return (entry.getUnixMode() & octal_0111) != 0;
            }

            @Override
            public @Nullable String getLinkTarget() throws IOException {
              return zipFile.getUnixSymlink(entry);
            }

            @Override
            public @NotNull InputStream getInputStream() throws IOException {
              return zipFile.getInputStream(entry);
            }
          };
        }
      }, target, stripRoot);
    }
  }

  static void extractTarBz2(Path archiveFile, Path target, boolean stripRoot) throws Exception {
    extractTarBasedArchive(archiveFile, target, stripRoot, is -> {
      try {
        return new BZip2CompressorInputStream(is);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public static void extractTarGz(Path archiveFile, Path target, boolean stripRoot) throws Exception {
    extractTarBasedArchive(archiveFile, target, stripRoot, is -> {
      try {
        return new GzipCompressorInputStream(is);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private static void extractTarBasedArchive(Path archiveFile, Path target, boolean stripRoot, Function<? super InputStream, ? extends InputStream> unpacker)
    throws Exception {
    try (TarArchiveInputStream archive = new TarArchiveInputStream(
      unpacker.apply(new BufferedInputStream(Files.newInputStream(archiveFile))))) {
      genericExtract(archiveFile, new ArchiveContent() {
        @Override
        public @Nullable Entry getNextEntry() throws IOException {
          TarArchiveEntry entry = archive.getNextTarEntry();
          if (entry == null) {
            return null;
          }

          return new Entry() {
            @Override
            public @NotNull Type getType() {
              if (entry.isSymbolicLink()) {
                return Type.SYMLINK;
              }

              if (entry.isDirectory()) {
                return Type.DIR;
              }

              if (entry.isFile()) {
                return Type.FILE;
              }

              throw new IllegalStateException(archiveFile + ": unknown entry type at '" + entry.getName() + "'");
            }

            @Override
            public @NotNull String getName() {
              return entry.getName();
            }

            @Override
            public boolean isExecutable() {
              return (entry.getMode() & octal_0111) != 0;
            }

            @Override
            public @Nullable String getLinkTarget() {
              return entry.getLinkName();
            }

            @Override
            public @NotNull InputStream getInputStream() {
              return CloseShieldInputStream.wrap(archive);
            }
          };
        }
      }, target, stripRoot);
    }
  }

  private static void genericExtract(Path archiveFile, ArchiveContent archive, Path target, boolean stripRoot) throws IOException {
    // avoid extra createDirectories calls
    Set<Path> createdDirs = new HashSet<>();
    EntryNameConverter converter = new EntryNameConverter(archiveFile, target, stripRoot);
    Path canonicalTarget = target.normalize();

    while (true) {
      Entry entry = archive.getNextEntry();
      if (entry == null) {
        break;
      }

      Entry.Type type = entry.getType();

      Path entryPath = converter.getOutputPath(entry.getName(), type == Entry.Type.DIR);
      if (entryPath == null) {
        continue;
      }

      if (type == Entry.Type.DIR) {
        Files.createDirectories(entryPath);
        createdDirs.add(entryPath);
      }
      else {
        Path parent = entryPath.getParent();
        if (createdDirs.add(parent)) {
          Files.createDirectories(parent);
        }

        if (type == Entry.Type.SYMLINK) {
          Path relativeSymlinkTarget = Path.of(Objects.requireNonNull(entry.getLinkTarget()));

          Path resolvedTarget = entryPath.resolveSibling(relativeSymlinkTarget).normalize();
          if (!resolvedTarget.startsWith(canonicalTarget) || resolvedTarget.equals(canonicalTarget)) {
            LOG.fine(archiveFile + ": skipping symlink entry '" + entry.getName() +
              "' which points outside of archive extraction directory, which is forbidden.\n" +
              "resolved target = " + resolvedTarget + "\n" +
              "root = " + canonicalTarget + "\n");
            continue;
          }

          if (isWindows) {
            // On Windows symlink creation is still gated by various registry keys

            if (Files.isRegularFile(resolvedTarget)) {
              Files.copy(resolvedTarget, entryPath);
            }
          }
          else {
            Files.createSymbolicLink(entryPath, relativeSymlinkTarget);
          }
        }
        else if (type == Entry.Type.FILE) {
          try (InputStream is = entry.getInputStream()) {
            Files.copy(is, entryPath);
          }

          if (isPosix && entry.isExecutable()) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"));
          }
        }
        else {
          throw new IllegalStateException("Unknown entry type: " + type);
        }
      }
    }
  }

  private interface ArchiveContent {
    @Nullable Entry getNextEntry() throws IOException;
  }

  private interface Entry {
    enum Type {FILE, DIR, SYMLINK}

    @NotNull
    Type getType();

    @NotNull
    String getName();

    boolean isExecutable();

    @Nullable
    String getLinkTarget() throws IOException;

    // entry should be the current entry
    @NotNull InputStream getInputStream() throws IOException;
  }

  private static final class EntryNameConverter {
    private final Path target;
    private final Path archiveFile;
    private final boolean stripRoot;

    private String leadingComponentPrefix = null;

    private EntryNameConverter(Path archiveFile, Path target, boolean stripRoot) {
      this.archiveFile = archiveFile;
      this.stripRoot = stripRoot;
      this.target = target;
    }

    private @Nullable Path getOutputPath(String entryName, boolean isDirectory) {
      String normalizedName = normalizeEntryName(entryName);
      if (!stripRoot) {
        return target.resolve(normalizedName);
      }

      if (leadingComponentPrefix == null) {
        String[] split = normalizedName.split(Character.toString(forwardSlash), 2);
        leadingComponentPrefix = split[0] + forwardSlash;

        if (split.length < 2) {
          if (!isDirectory) {
            throw new IllegalStateException(archiveFile + ": first top-level entry must be a directory if strip root is enabled");
          }

          return null;
        }
        else {
          return target.resolve(split[1]);
        }
      }

      if (!normalizedName.startsWith(leadingComponentPrefix)) {
        throw new IllegalStateException(
          archiveFile + ": entry name '" + normalizedName + "' should start with previously found prefix '" + leadingComponentPrefix + "'");
      }

      return target.resolve(normalizedName.substring(leadingComponentPrefix.length()));
    }
  }

  static String normalizeEntryName(String name) {
    String withForwardSlashes = name.replace(backwardSlash, forwardSlash);
    String trimmed = trim(withForwardSlashes, forwardSlash);
    assertValidEntryName(trimmed);
    return trimmed;
  }

  private static final char backwardSlash = '\\';
  private static final String backwardSlashString = Character.toString(backwardSlash);
  private static final char forwardSlash = '/';
  private static final String forwardSlashString = Character.toString(forwardSlash);
  private static final String doubleForwardSlashString = forwardSlashString + forwardSlashString;

  private static void assertValidEntryName(String normalizedEntryName) {
    if (normalizedEntryName.isBlank()) {
      throw new IllegalStateException("Entry names should not be blank: '" + normalizedEntryName + "'");
    }
    if (normalizedEntryName.contains(backwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not contain '" + backwardSlashString + "'");
    }
    if (normalizedEntryName.startsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not start with forward slash: " + normalizedEntryName);
    }
    if (normalizedEntryName.endsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not end with forward slash: " + normalizedEntryName);
    }
    if (normalizedEntryName.contains(doubleForwardSlashString)) {
      throw new IllegalStateException(
        "Normalized entry name should not contain '" + doubleForwardSlashString + "': " + normalizedEntryName);
    }
    if (normalizedEntryName.contains("..") &&
        Arrays.asList(normalizedEntryName.split(forwardSlashString)).contains("..")) {
      throw new IllegalStateException("Invalid entry name: " + normalizedEntryName);
    }
  }

  static String trim(String s, char charToTrim) {
    int len = s.length();
    int start = 0;
    while (start < len && s.charAt(start) == charToTrim) start++;
    int end = len;
    while (end > 0 && start < end && s.charAt(end - 1) == charToTrim) end--;
    return s.substring(start, end);
  }

  public static void deleteFileOrFolder(Path file) {
    try {
      MoreFiles.deleteRecursively(file, RecursiveDeleteOption.ALLOW_INSECURE);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void cleanDirectory(Path directory) throws IOException {
    Files.createDirectories(directory);
    try (Stream<Path> stream = Files.list(directory)) {
      stream.forEach(BuildDependenciesUtil::deleteFileOrFolder);
    }
  }

  public static Map<String, String> loadPropertiesFile(Path file) {
    HashMap<String, String> result = new HashMap<>();

    Properties properties = new Properties();
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      result.put((String)entry.getKey(), (String)entry.getValue());
    }

    return result;
  }

  static List<Path> listDirectory(Path directory) {
    try (Stream<Path> stream = Files.list(directory)) {
      return stream.collect(Collectors.toList());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
