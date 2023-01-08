// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Inspection implements Comparable<Inspection> {
  private static final Safelist WHITELIST = new Safelist()
    .addTags("p", "br", "li", "ul", "ol", "b", "i", "code", "a")
    .addAttributes("a", "href");
  public String id;

  public String name = "";
  public String severity = "WARNING";
  public List<String> path = new ArrayList<>();
  public String language = "";
  public boolean appliesToDialects = true;
  public boolean isCleanup = false;
  public boolean isEnabledDefault = true;
  public String briefDescription = "";
  public String extendedDescription = "";
  public boolean hasOptionsPanel = false;
  public OptionsPanelInfo optionsPanelInfo = null;

  public Inspection(String id,
                    String name,
                    String severity,
                    String language,
                    String briefDescription,
                    String extendedDescription,
                    List<String> path,
                    boolean appliesToDialects,
                    boolean partOfCodeCleanup,
                    boolean enabledByDefault,
                    OptionsPanelInfo optionsPanelInfo) {
    this.id = id;
    this.name = name;
    this.severity = severity;
    this.language = language;
    this.briefDescription = briefDescription;
    this.extendedDescription = extendedDescription;
    this.path = path;
    this.appliesToDialects = appliesToDialects;
    this.isCleanup = partOfCodeCleanup;
    this.isEnabledDefault = enabledByDefault;
    this.hasOptionsPanel = optionsPanelInfo != null;
    this.optionsPanelInfo = optionsPanelInfo;
  }

  public Inspection() {
  }

  public boolean isAppliesToDialects() {
    return appliesToDialects;
  }

  public boolean isCleanup() {
    return isCleanup;
  }

  public boolean isEnabledDefault() {
    return isEnabledDefault;
  }

  public boolean isHasOptionsPanel() {
    return hasOptionsPanel;
  }

  @NotNull
  String cleanHtml(final @NotNull String src) {
    final Document doc = Jsoup.parse(Jsoup.clean(src, WHITELIST));

    doc.select("ul").forEach(e -> e.tagName("list"));
    doc.select("ol").forEach(e -> {
      e.tagName("list");
      e.attr("type", "decimal");
    });

    doc.select("code").forEach(element -> element.text(element.text()));

    doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

    return doc.body().html();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSeverity() {
    return severity;
  }

  public String getLanguage() {
    return language;
  }

  public String getExtendedDescription() {
    return extendedDescription;
  }

  public String getBriefDescription() {
    return briefDescription;
  }

  public List<String> getPath() {
    return List.copyOf(path);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Inspection)) return false;
    Inspection that = (Inspection)o;
    return appliesToDialects == that.appliesToDialects &&
           isCleanup == that.isCleanup &&
           isEnabledDefault == that.isEnabledDefault &&
           hasOptionsPanel == that.hasOptionsPanel &&
           id.equals(that.id) &&
           name.equals(that.name) &&
           severity.equals(that.severity) &&
           path.equals(that.path) &&
           language.equals(that.language) &&
           Objects.equals(briefDescription, that.briefDescription) &&
           Objects.equals(extendedDescription, that.extendedDescription) &&
           Objects.equals(optionsPanelInfo, that.optionsPanelInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, severity, path, language, appliesToDialects, isCleanup, isEnabledDefault, briefDescription,
                        extendedDescription, hasOptionsPanel, optionsPanelInfo);
  }

  @Override
  public int compareTo(@NotNull Inspection o) {
    return name.compareTo(o.name);
  }
}
