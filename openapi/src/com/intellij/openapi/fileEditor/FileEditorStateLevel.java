/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

/**
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public final class FileEditorStateLevel {
  public static final FileEditorStateLevel FULL = new FileEditorStateLevel("full");
  public static final FileEditorStateLevel UNDO = new FileEditorStateLevel("undo");
  public static final FileEditorStateLevel NAVIGATION = new FileEditorStateLevel("navigation");

  private final String myText;

  private FileEditorStateLevel(final String text) {
    myText = text;
  }

  public String toString() {
    return "FileEditorStateLevel["+myText+"]";
  }
}
