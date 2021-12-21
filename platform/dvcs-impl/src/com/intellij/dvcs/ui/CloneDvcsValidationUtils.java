// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.regex.Pattern;

import static kotlin.text.StringsKt.removePrefix;

public final class CloneDvcsValidationUtils {
  /**
   * The pattern for SSH URL-s in form [user@]host:path
   */
  private static final Pattern SSH_URL_PATTERN;

  static {
    // TODO make real URL pattern
    @NonNls final String ch = "[\\p{ASCII}&&[\\p{Graph}]&&[^@:/]]";
    @NonNls final String host = ch + "+(?:\\." + ch + "+)*";
    @NonNls final String path = "/?" + ch + "+(?:/" + ch + "+)*/?";
    @NonNls final String all = "(?:" + ch + "+@)?" + host + ":" + path;
    SSH_URL_PATTERN = Pattern.compile(all);
  }


  @Nullable
  public static ValidationInfo createDestination(@NotNull String path) {
    try {
      Path directoryPath = Paths.get(path);
      if (!directoryPath.toFile().exists()) {
        Files.createDirectories(directoryPath);
      }
      else if (!directoryPath.toFile().isDirectory()) {
        return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.access")).withOKEnabled();
      }
      return null;
    }
    catch (InvalidPathException e) {
      return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.invalid"));
    }
    catch (Exception e) {
      return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.access")).withOKEnabled();
    }
  }

  /**
   * Check destination directory and set appropriate error text if there are problems
   *
   * @return null if destination directory is OK.
   */
  @Nullable
  public static ValidationInfo checkDirectory(@NotNull String directoryPath, @NotNull JComponent component) {
    if (directoryPath.length() == 0) {
      return new ValidationInfo("");
    }

    try {
      Path path = Paths.get(directoryPath);
      if (!path.toFile().exists()) {
        return null;
      }
      else if (!path.toFile().isDirectory()) {
        return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.not.directory"), component);
      }
      else if (!isDirectoryEmpty(path)) {
        return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.exists"), component);
      }
    }
    catch (InvalidPathException | IOException e) {
      return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.invalid"), component);
    }
    return null;
  }

  private static boolean isDirectoryEmpty(@NotNull Path directory) throws IOException {
    DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory);
    return !directoryStream.iterator().hasNext();
  }

  /**
   * Check repository URL and set appropriate error text if there are problems
   *
   * @return null if repository URL is OK.
   */
  @Nullable
  public static ValidationInfo checkRepositoryURL(JComponent component, String repository) {
    if (repository.length() == 0) {
      return new ValidationInfo(DvcsBundle.message("clone.repository.url.error.empty"), component);
    }

    repository = sanitizeCloneUrl(repository);

    // Is it a proper URL?
    try {
      if (new URI(repository).isAbsolute()) {
        return null;
      }
    }
    catch (URISyntaxException urlExp) {
      // do nothing
    }

    // Is it SSH URL?
    if (SSH_URL_PATTERN.matcher(repository).matches()) {
      return null;
    }

    // Is it FS URL?
    try {
      Path path = Paths.get(repository);

      if (path.toFile().exists()) {
        if (!path.toFile().isDirectory()) {
          return new ValidationInfo(DvcsBundle.message("clone.repository.url.error.not.directory"), component);
        }
        return null;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }

    return new ValidationInfo(DvcsBundle.message("clone.repository.url.error.invalid"), component);
  }

  @NotNull
  static String sanitizeCloneUrl(@NotNull String urlText) {
    return removePrefix(removePrefix(urlText.trim(), "git clone"), "hg clone").trim(); //NON-NLS
  }
}