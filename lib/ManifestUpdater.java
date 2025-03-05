// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class ManifestUpdater {
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: <input-jar> <module name> <output-jar>, but got: " + Arrays.toString(args) + " arguments.");
      return;
    }

    Path inputJar = Path.of(args[0]);
    Path outputFile = Path.of(args[1]);
    String moduleName = outputFile.getFileName().toString();
    moduleName = moduleName.substring(0, moduleName.length() - 4);

    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.put(new Attributes.Name("Automatic-Module-Name"), moduleName);
    try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(outputFile))) {
      outputStream.setLevel(ZipOutputStream.STORED);

      ZipEntry manifestEntry = new ZipEntry(JarFile.MANIFEST_NAME);
      outputStream.putNextEntry(manifestEntry);
      manifest.write(new BufferedOutputStream(outputStream));
      outputStream.closeEntry();

      // Copy entries from original JAR to new JAR
      try (ZipFile jar = new ZipFile(inputJar.toFile())) {
        Enumeration<? extends ZipEntry> entries = jar.entries();
        byte[] buffer = new byte[8192];
        while (entries.hasMoreElements()) {
          ZipEntry jarEntry = entries.nextElement();
          // ignore existing manifest
          String entryName = jarEntry.getName();
          if (entryName.equals("META-INF/MANIFEST.MF")) {
            continue;
          }

          try {
            outputStream.putNextEntry(new ZipEntry(entryName));
            if (!jarEntry.isDirectory()) {
              InputStream entryStream = jar.getInputStream(jarEntry);
              int bytesRead;
              while ((bytesRead = entryStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
              }
            }
            outputStream.closeEntry();
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
    }
  }
}