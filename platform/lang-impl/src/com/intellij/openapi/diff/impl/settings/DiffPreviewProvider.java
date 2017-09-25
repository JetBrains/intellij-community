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

package com.intellij.openapi.diff.impl.settings;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 * Implement this interface to enable custom diff preview in Colors & Fonts Settings page
 */
public abstract class DiffPreviewProvider {
  public static final ExtensionPointName<DiffPreviewProvider> EP_NAME = ExtensionPointName.create("com.intellij.diffPreviewProvider");

  @NotNull
  public abstract DiffContent[] createContents();

  @NotNull
  public static DiffContent[] getContents() {
    // Assuming that standalone IDE should provide one provider
    final DiffPreviewProvider[] providers = Extensions.getExtensions(EP_NAME);
    if (providers.length != 0) {
      return providers[0].createContents();
    }
    return createContent(LEFT_TEXT, CENTER_TEXT, RIGHT_TEXT, StdFileTypes.JAVA);
  }

  @NotNull
  public static DiffContent[] createContent(@NotNull String left,
                                            @NotNull String center,
                                            @NotNull String right,
                                            @NotNull FileType fileType) {
    return new DiffContent[]{
      createContent(left, fileType),
      createContent(center, fileType),
      createContent(right, fileType)};
  }

  @NotNull
  private static DiffContent createContent(@NotNull String text, @NotNull FileType fileType) {
    return DiffContentFactory.getInstance().create(text, fileType);
  }

  @NonNls private static final String LEFT_TEXT = "class MyClass {\n" +
                                                  "  int value;\n" +
                                                  "\n" +
                                                  "  void leftOnly() {}\n" +
                                                  "\n" +
                                                  "  void foo() {\n" +
                                                  "   // Left changes\n" +
                                                  "  }\n" +
                                                  "\n" +
                                                  "  void bar() {\n" +
                                                  "\n" +
                                                  "  }\n" +
                                                  "}\n" +
                                                  "\n" +
                                                  "\n";
  @NonNls private static final String CENTER_TEXT = "class MyClass {\n" +
                                                    "  int value;\n" +
                                                    "\n" +
                                                    "  void foo() {\n" +
                                                    "  }\n" +
                                                    "\n" +
                                                    "  void removedFromLeft() {}\n" +
                                                    "\n" +
                                                    "  void bar() {\n" +
                                                    "\n" +
                                                    "  }\n" +
                                                    "}\n" +
                                                    "\n" +
                                                    "\n";
  @NonNls private static final String RIGHT_TEXT = "class MyClass {\n" +
                                                   "  long value;\n" +
                                                   "\n" +
                                                   "  void foo() {\n" +
                                                   "   // Right changes\n" +
                                                   "  }\n" +
                                                   "\n" +
                                                   "  void removedFromLeft() {}\n" +
                                                   "\n" +
                                                   "  void bar() {\n" +
                                                   "  }\n" +
                                                   "\n" +
                                                   "}\n" +
                                                   "\n" +
                                                   "\n";
}
