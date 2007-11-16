package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.util.PairConsumer;
import com.intellij.ide.highlighter.UnknownFileType;
import com.intellij.ide.highlighter.ArchiveFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull PairConsumer<FileType, String> consumer) {
    consumer.consume(new ArchiveFileType(), "zip;jar;war;ear");
    consumer.consume(new PlainTextFileType(), "txt;sh;bat;cmd;policy;log;cgi;pl;MF;sql;jad;jam");
    consumer.consume(new UnknownFileType(), null);
  }
}