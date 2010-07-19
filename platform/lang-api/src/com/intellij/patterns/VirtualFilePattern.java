/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.patterns;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public class VirtualFilePattern extends TreeElementPattern<VirtualFile, VirtualFile, VirtualFilePattern> {
  public VirtualFilePattern() {
    super(VirtualFile.class);
  }

  public VirtualFilePattern ofType(final FileType type) {
    return with(new PatternCondition<VirtualFile>("ofType") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return type.equals(virtualFile.getFileType());
      }
    });
  }

  public VirtualFilePattern withName(final String name) {
    return withName(PlatformPatterns.string().equalTo(name));
  }

  public VirtualFilePattern withExtension(@NotNull final String... alternatives) {
    return with(new PatternCondition<VirtualFile>("withExtension") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        final String extension = virtualFile.getExtension();
        for (String alternative : alternatives) {
          if (alternative.equals(extension)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public VirtualFilePattern withExtension(@NotNull final String extension) {
    return with(new PatternCondition<VirtualFile>("withExtension") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return extension.equals(virtualFile.getExtension());
      }
    });
  }

  public VirtualFilePattern withName(final ElementPattern<String> namePattern) {
    return with(new PatternCondition<VirtualFile>("withName") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return namePattern.getCondition().accepts(virtualFile.getName(), context);
      }
    });
  }

  public VirtualFilePattern xmlWithRootTag(final ElementPattern<String> tagNamePattern) {
    return with(new PatternCondition<VirtualFile>("xmlWithRootTag") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        try {
          String tagName = NanoXmlUtil.parseHeaderWithException(virtualFile).getRootTagLocalName();
          return tagName != null && tagNamePattern.getCondition().accepts(tagName, context);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }
  public VirtualFilePattern xmlWithRootTagNamespace(final String... namespace) {
    StringPattern namespacePattern = StandardPatterns.string().oneOf(namespace);
    return xmlWithRootTagNamespace(namespacePattern);
  }

  public VirtualFilePattern xmlWithRootTagNamespace(final ElementPattern<String> namespacePattern) {
    return with(new PatternCondition<VirtualFile>("xmlWithRootTagNamespace") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        try {
          String rootTagNamespace = NanoXmlUtil.parseHeaderWithException(virtualFile).getRootTagNamespace();
          return rootTagNamespace != null && namespacePattern.getCondition().accepts(rootTagNamespace, context);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  protected VirtualFile getParent(@NotNull final VirtualFile t) {
    return t.getParent();
  }

  protected VirtualFile[] getChildren(@NotNull final VirtualFile file) {
    return file.getChildren();
  }
}
