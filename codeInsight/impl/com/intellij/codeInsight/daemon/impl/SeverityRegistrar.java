/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 24-Feb-2006
 */
public class SeverityRegistrar implements JDOMExternalizable, ApplicationComponent {
  @NonNls private static final String INFO = "info";
  private static final Map<HighlightSeverity, HighlightInfoType.HighlightInfoTypeImpl> ourMap = new TreeMap<HighlightSeverity, HighlightInfoType.HighlightInfoTypeImpl>();
  private static final Map<HighlightSeverity, Color> ourRendererColors = new HashMap<HighlightSeverity, Color>();
  @NonNls private static final String ERROR = "error";
  @NonNls private static final String WARNING = "warning";
  @NonNls private static final String COLOR = "color";
  @NonNls private static final String INFORMATION = "information";
  @NonNls private static final String SERVER = "server";

  public static SeverityRegistrar getInstance(){
    return ApplicationManager.getApplication().getComponent(SeverityRegistrar.class);
  }

  public static void registerSeverity(HighlightInfoType.HighlightInfoTypeImpl info, Color renderColor){
    final HighlightSeverity severity = info.getSeverity(null);
    ourMap.put(severity, info);
    ourRendererColors.put(severity, renderColor);
    HighlightDisplayLevel.registerSeverity(severity, renderColor);
  }

  public static Collection<HighlightInfoType.HighlightInfoTypeImpl> getRegisteredHighlightingInfoTypes() {
    return ourMap.values();
  }

  public static HighlightInfoType.HighlightInfoTypeImpl unregisterSeverity(HighlightSeverity severity){
    return ourMap.remove(severity);
  }

  public static HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(HighlightSeverity severity) {
    if (severity == HighlightSeverity.ERROR){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.ERROR;
    }
    if (severity == HighlightSeverity.WARNING){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.WARNING;
    }
    if (severity == HighlightSeverity.INFO){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFO;
    }
    if (severity == HighlightSeverity.INFORMATION){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFORMATION;
    }
    if (severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING){
      return (HighlightInfoType.HighlightInfoTypeImpl) HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER;
    }
    final HighlightInfoType.HighlightInfoTypeImpl infoType = ourMap.get(severity);
    return (HighlightInfoType.HighlightInfoTypeImpl)(infoType != null ? infoType : HighlightInfoType.WARNING);
  }



  public void readExternal(Element element) throws InvalidDataException {
    ourMap.clear();
    ourRendererColors.clear();
    final List children = element.getChildren(INFO);
    if (children != null){
      for (Object child : children) {
        HighlightInfoType.HighlightInfoTypeImpl info = new HighlightInfoType.HighlightInfoTypeImpl();
        final Element infoElement = (Element)child;
        info.readExternal(infoElement);
        Color color = null;
        final String colorStr = infoElement.getAttributeValue(COLOR);
        if (colorStr != null){
          color = new Color(Integer.parseInt(colorStr, 16));
        }
        registerSeverity(info, color);
      }
    }

    final Element error = element.getChild(ERROR);
    if (error != null) {
      HighlightSeverity.ERROR.readExternal(error);
    }

    final Element warning = element.getChild(WARNING);
    if (warning != null){
      HighlightSeverity.WARNING.readExternal(warning);
    }

    final Element info = element.getChild(INFORMATION);
    if (info != null){
      HighlightSeverity.INFO.readExternal(info);
    }

    final Element server = element.getChild(SERVER);
    if (server != null){
      HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.readExternal(server);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (HighlightSeverity severity : ourMap.keySet()) {
      Element info = new Element(INFO);
      final HighlightInfoType.HighlightInfoTypeImpl infoType = ourMap.get(severity);
      infoType.writeExternal(info);
      final Color color = ourRendererColors.get(severity);
      if (color != null) {
        info.setAttribute(COLOR, Integer.toString(color.getRGB() & 0xFFFFFF, 16));
      }
      element.addContent(info);
    }
    Element errorSeverity = new Element(ERROR);
    HighlightSeverity.ERROR.writeExternal(errorSeverity);
    element.addContent(errorSeverity);

    Element warningSeverity = new Element(WARNING);
    HighlightSeverity.WARNING.writeExternal(warningSeverity);
    element.addContent(warningSeverity);

    Element infoSeverity = new Element(INFORMATION);
    HighlightSeverity.INFO.writeExternal(infoSeverity);
    element.addContent(infoSeverity);

    Element serverSeverity = new Element(SERVER);
    HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.writeExternal(serverSeverity);
    element.addContent(serverSeverity);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "SeverityRegistrar";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static int getSeveritiesCount() {
    return createCurrentSeveritiesSet().size();
  }

  public static HighlightSeverity getSeverityByIndex(final int i) {
    TreeSet<HighlightSeverity> set = createCurrentSeveritiesSet();
    int index = 0;
    for (HighlightSeverity severity : set) {
      if (index == i) return severity;
      index++;
    }
    return null;
  }

  private static TreeSet<HighlightSeverity> createCurrentSeveritiesSet() {
    TreeSet<HighlightSeverity> set = new TreeSet<HighlightSeverity>();
    set.add(HighlightSeverity.ERROR);
    set.add(HighlightSeverity.WARNING);
    set.add(HighlightSeverity.INFO);
    set.add(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING);
    set.addAll(ourMap.keySet());
    return set;
  }

  public static Color getRendererColorByIndex(final int i) {
    final HighlightSeverity severity = getSeverityByIndex(i);
    if (severity == HighlightSeverity.ERROR){
      return CodeInsightColors.ERRORS_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor();
    }
    if (severity == HighlightSeverity.WARNING){
      return CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor();
    }
    if (severity == HighlightSeverity.INFO){
      return CodeInsightColors.INFO_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor();
    }
    if (severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING){
      return CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING.getDefaultAttributes().getErrorStripeColor();
    }

    return ourRendererColors.get(severity);
  }

  public static boolean isSeverityValid(final HighlightSeverity severity) {
    return createCurrentSeveritiesSet().contains(severity);
  }

  public static HighlightSeverity getSeverityByName(String severityName){
    for (HighlightSeverity severity : ourMap.keySet()) {
      if (severity.myName.equals(severityName)) return severity;
    }
    return HighlightSeverity.WARNING;
  }
}
