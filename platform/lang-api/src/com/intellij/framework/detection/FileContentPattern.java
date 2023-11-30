// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;

/**
 * Provides filters for file content
 */
public final class FileContentPattern extends ObjectPattern<FileContent, FileContentPattern> {
  private FileContentPattern() {
    super(FileContent.class);
  }

  public static FileContentPattern fileContent() {
    return new FileContentPattern();
  }

  public FileContentPattern withName(final @NotNull String name) {
    return with(new PatternCondition<>("withName") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return name.equals(fileContent.getFileName());
      }
    });
  }

  public FileContentPattern withName(final StringPattern namePattern) {
    return with(new PatternCondition<>("withName") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return namePattern.accepts(fileContent.getFileName());
      }
    });
  }

  public FileContentPattern inDirectory(final @NotNull String name) {
    return with(new PatternCondition<>("inDirectory") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return name.equals(fileContent.getFile().getParent().getName());
      }
    });
  }

  public FileContentPattern xmlWithRootTag(final @NotNull String rootTag) {
    return with(new PatternCondition<>("withRootTag") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        try {
          return rootTag.equals(parseHeaderWithException(CharArrayUtil.readerFromCharSequence(fileContent.getContentAsText())).getRootTagLocalName());
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  public FileContentPattern xmlWithRootTagNamespace(final String namespace) {
    return xmlWithRootTagNamespace(StandardPatterns.string().equalTo(namespace));
  }

  public FileContentPattern xmlWithRootTagNamespace(final ElementPattern<String> namespacePattern) {
    return with(new PatternCondition<>("xmlWithRootTagNamespace") {
      @Override
      public boolean accepts(final @NotNull FileContent fileContent, final ProcessingContext context) {
        try {
          String rootTagNamespace = parseHeaderWithException(CharArrayUtil.readerFromCharSequence(fileContent.getContentAsText())).getRootTagNamespace();
          return rootTagNamespace != null && namespacePattern.accepts(rootTagNamespace, context);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  public interface ParseXml {
    @NotNull XmlFileHeader parseHeaderWithException(@NotNull Reader reader);
    static ParseXml getInstance() {
      return ApplicationManager.getApplication().getService(ParseXml.class);
    }
  }

  private static @NotNull XmlFileHeader parseHeaderWithException(@NotNull Reader reader) throws IOException {
    return ParseXml.getInstance().parseHeaderWithException(reader);
  }
}
