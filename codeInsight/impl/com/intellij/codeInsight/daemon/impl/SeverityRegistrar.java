/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 24-Feb-2006
 */
public class SeverityRegistrar implements JDOMExternalizable, Comparator<HighlightSeverity> {
  @NonNls private static final String INFO = "info";
  private final Map<HighlightSeverity, SeverityBasedTextAttributes> ourMap = new THashMap<HighlightSeverity, SeverityBasedTextAttributes>();
  private final Map<HighlightSeverity, Color> ourRendererColors = new THashMap<HighlightSeverity, Color>();
  @NonNls private static final String COLOR = "color";

  private JDOMExternalizableStringList myOrder = new JDOMExternalizableStringList();

  public static SeverityRegistrar getInstance() {
    return InspectionProfileManager.getInstance().getSeverityRegistrar();
  }

  public static SeverityRegistrar getInstance(@Nullable Project project) {
    return project != null ? InspectionProjectProfileManager.getInstance(project).getSeverityRegistrar() : getInstance();
  }

  public void registerSeverity(SeverityBasedTextAttributes info, Color renderColor){
    final HighlightSeverity severity = info.getType().getSeverity(null);
    ourMap.put(severity, info);
    ourRendererColors.put(severity, renderColor);
    HighlightDisplayLevel.registerSeverity(severity, renderColor);
  }

  public Collection<SeverityBasedTextAttributes> getRegisteredHighlightingInfoTypes() {
    return ourMap.values();
  }

  public SeverityBasedTextAttributes unregisterSeverity(HighlightSeverity severity){
    return ourMap.remove(severity);
  }

  public HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(HighlightSeverity severity) {
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
    final SeverityBasedTextAttributes infoType = ourMap.get(severity);
    return (HighlightInfoType.HighlightInfoTypeImpl)(infoType != null ? infoType.getType() : HighlightInfoType.WARNING);
  }

  @Nullable
  public TextAttributes getTextAttributesBySeverity(HighlightSeverity severity) {
    final SeverityBasedTextAttributes infoType = ourMap.get(severity);
    if (infoType != null) {
      return infoType.getAttributes();
    }
    return null;
  }


  public void readExternal(Element element) throws InvalidDataException {
    ourMap.clear();
    ourRendererColors.clear();
    final List children = element.getChildren(INFO);
    if (children != null){
      for (Object child : children) {
        final Element infoElement = (Element)child;

        final SeverityBasedTextAttributes highlightInfo = new SeverityBasedTextAttributes();
        highlightInfo.readExternal(infoElement);

        Color color = null;
        final String colorStr = infoElement.getAttributeValue(COLOR);
        if (colorStr != null){
          color = new Color(Integer.parseInt(colorStr, 16));
        }
        registerSeverity(highlightInfo, color);
      }
    }
    myOrder.clear();
    myOrder.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (HighlightSeverity severity : ourMap.keySet()) {
      Element info = new Element(INFO);
      final SeverityBasedTextAttributes infoType = ourMap.get(severity);
      infoType.writeExternal(info);
      final Color color = ourRendererColors.get(severity);
      if (color != null) {
        info.setAttribute(COLOR, Integer.toString(color.getRGB() & 0xFFFFFF, 16));
      }
      element.addContent(info);
    }
    myOrder.writeExternal(element);
  }

  public int getSeveritiesCount() {
    return createCurrentSeverities().size();
  }

  public HighlightSeverity getSeverityByIndex(final int i) {
    return getSeverity(getOrder().get(i));
  }

  public HighlightSeverity getSeverity(final String name) {
    final List<HighlightSeverity> list = createCurrentSeverities();
    for (HighlightSeverity severity : list) {
      if (Comparing.strEqual(name, severity.toString())) return severity;
    }
    return null;
  }

  private List<HighlightSeverity> createCurrentSeverities() {
    List<HighlightSeverity> list = new ArrayList<HighlightSeverity>();
    list.add(HighlightSeverity.ERROR);
    list.add(HighlightSeverity.WARNING);
    list.add(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING);
    list.add(HighlightSeverity.INFO);
    list.addAll(ourMap.keySet());
    Collections.sort(list);
    return list;
  }

  public Icon getRendererIconByIndex(final int i) {
    final HighlightSeverity severity = getSeverityByIndex(i);
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return level.getIcon();
    }

    return HighlightDisplayLevel.createIconByMask(ourRendererColors.get(severity));
  }

  public boolean isSeverityValid(final HighlightSeverity severity) {
    return createCurrentSeverities().contains(severity);
  }

  public int compare(final HighlightSeverity s1, final HighlightSeverity s2) {
    return getOrder().indexOf(s1.myName) - getOrder().indexOf(s2.myName);
  }

  public JDOMExternalizableStringList getOrder() {
    if (myOrder.isEmpty()) {
      final List<HighlightSeverity> severities = createCurrentSeverities();
      for (HighlightSeverity severity : severities) {
        myOrder.add(severity.toString());
      }
    }
    return myOrder;
  }

  public void setOrder(List<String> order) {
    myOrder.clear();
    myOrder.addAll(order);
  }

  public static class SeverityBasedTextAttributes implements JDOMExternalizable {
    private TextAttributes myAttributes;
    private HighlightInfoType.HighlightInfoTypeImpl myType;

    //readexternal
    public SeverityBasedTextAttributes() {
      myAttributes = new TextAttributes();
      myType = new HighlightInfoType.HighlightInfoTypeImpl();
    }

    public SeverityBasedTextAttributes(final TextAttributes attributes, final HighlightInfoType.HighlightInfoTypeImpl type) {
      myAttributes = attributes;
      myType = type;
    }

    public TextAttributes getAttributes() {
      return myAttributes;
    }

    public HighlightInfoType.HighlightInfoTypeImpl getType() {
      return myType;
    }

    public void readExternal(Element element) throws InvalidDataException {
      myAttributes.readExternal(element);
      myType.readExternal(element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      myAttributes.writeExternal(element);
      myType.writeExternal(element);
    }

    public HighlightSeverity getSeverity() {
      return myType.getSeverity(null);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SeverityBasedTextAttributes that = (SeverityBasedTextAttributes)o;

      if (myAttributes != null ? !myAttributes.equals(that.myAttributes) : that.myAttributes != null) return false;
      if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (myAttributes != null ? myAttributes.hashCode() : 0);
      result = 31 * result + (myType != null ? myType.hashCode() : 0);
      return result;
    }
  }
}
