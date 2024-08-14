// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc.markdown;

import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor;
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.markerblocks.providers.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class JavaDocMarkerProcessorFactory implements MarkerProcessorFactory {
  @NotNull
  @Override
  public MarkerProcessor<?> createMarkerProcessor(@NotNull ProductionHolder holder) {
    return new JavaDocMarkerProcessor(holder);
  }

  /**
   * Marker processor taking account of changes brought to Common Markdown by the JEP-467
   * Namely: no indented code blocks, tables from Github Markdown
   */
  public static class JavaDocMarkerProcessor extends CommonMarkMarkerProcessor {
    public JavaDocMarkerProcessor(@NotNull ProductionHolder productionHolder) {
      super(productionHolder, CommonMarkdownConstraints.Companion.getBASE());
    }

    @NotNull
    @Override
    protected List<MarkerBlockProvider<StateInfo>> getMarkerBlockProviders() {
      return Arrays.asList(new HorizontalRuleProvider(),
                           new CodeFenceProvider(),
                           new SetextHeaderProvider(),
                           new BlockQuoteProvider(),
                           new ListMarkerProvider(),
                           new AtxHeaderProvider(),
                           new HtmlBlockProvider(),
                           new GitHubTableMarkerProvider());
    }
  }
}
