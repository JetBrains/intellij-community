/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import org.jetbrains.annotations.NotNull;

public class CoreFileTypeFactory  extends FileTypeFactory {
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(ArchiveFileType.INSTANCE, "zip;jar;war;ear;swc;ane;egg;apk");
    consumer.consume(PlainTextFileType.INSTANCE, "txt;sh;bat;cmd;policy;log;cgi;MF;jad;jam;htaccess;rb");
    consumer.consume(UnknownFileType.INSTANCE);
  }
}
