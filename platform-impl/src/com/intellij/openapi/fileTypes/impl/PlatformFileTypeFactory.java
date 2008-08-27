package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.UnknownFileType;
import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(new ArchiveFileType(), "zip;jar;war;ear;swc;egg"); // egg for python
    consumer.consume(new PlainTextFileType(),
                     new ExtensionFileNameMatcher("txt"),
                     new ExtensionFileNameMatcher("sh"),
                     new ExtensionFileNameMatcher("bat"),
                     new ExtensionFileNameMatcher("cmd"),
                     new ExtensionFileNameMatcher("policy"),
                     new ExtensionFileNameMatcher("log"),
                     new ExtensionFileNameMatcher("cgi"),
                     new ExtensionFileNameMatcher("pl"),
                     new ExtensionFileNameMatcher("MF"),
                     new ExtensionFileNameMatcher("sql"),
                     new ExtensionFileNameMatcher("jad"),
                     new ExtensionFileNameMatcher("jam"),
                     new ExactFileNameMatcher("readme", true));
    consumer.consume(new UnknownFileType());
  }
}