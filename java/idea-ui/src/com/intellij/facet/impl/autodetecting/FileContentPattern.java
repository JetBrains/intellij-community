/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.facet.impl.autodetecting;

import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.patterns.*;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public class FileContentPattern extends ObjectPattern<FileContent, FileContentPattern> {
  public FileContentPattern() {
    super(FileContent.class);
  }

  public static FileContentPattern fileContent() {
    return new FileContentPattern();
  }

  public FileContentPattern withName(@NotNull final String name) {
    return with(new PatternCondition<FileContent>("withName") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return name.equals(fileContent.getVirtualFile().getName());
      }
    });
  }

  public FileContentPattern withName(final StringPattern namePattern) {
    return with(new PatternCondition<FileContent>("withName") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return namePattern.accepts(fileContent.getVirtualFile().getName());
      }
    });
  }

  public FileContentPattern inDirectory(final @NotNull String name) {
    return with(new PatternCondition<FileContent>("inDirectory") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return name.equals(fileContent.getVirtualFile().getParent().getName());
      }
    });
  }

  public FileContentPattern xmlWithRootTag(@NotNull final String rootTag) {
    return with(new PatternCondition<FileContent>("withRootTag") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        try {
          return rootTag.equals(parseHeaderWithException(fileContent).getRootTagLocalName());
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
    return with(new PatternCondition<FileContent>("xmlWithRootTagNamespace") {
      public boolean accepts(@NotNull final FileContent fileContent, final ProcessingContext context) {
        try {
          String rootTagNamespace = parseHeaderWithException(fileContent).getRootTagNamespace();
          return rootTagNamespace != null && namespacePattern.getCondition().accepts(rootTagNamespace, context);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  public static FileContentPattern byFilter(@NotNull final VirtualFileFilter filter) {
    return fileContent().with(new PatternCondition<FileContent>("withFileFilter") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return filter.accept(fileContent.getVirtualFile());
      }
    });
  }

  public static FileContentPattern byFilePattern(@NotNull final VirtualFilePattern pattern) {
    return fileContent().with(new PatternCondition<FileContent>("withFilePattern") {
      @Override
      public boolean accepts(@NotNull FileContent fileContent, ProcessingContext context) {
        return pattern.accepts(fileContent.getVirtualFile());
      }
    });
  }

  @NotNull
  private static XmlFileHeader parseHeaderWithException(FileContent fileContent) throws IOException {
    final CharSequence contentText = CacheUtil.getContentText(fileContent);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return NanoXmlUtil.parseHeaderWithException(new CharSequenceReader(contentText));
  }
}
