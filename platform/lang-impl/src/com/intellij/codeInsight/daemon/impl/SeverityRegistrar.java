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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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
  private final Map<String, SeverityBasedTextAttributes> ourMap = new THashMap<String, SeverityBasedTextAttributes>();
  private final Map<String, Color> ourRendererColors = new THashMap<String, Color>();
  @NonNls private static final String COLOR = "color";

  private final OrderMap myOrder = new OrderMap();
  private JDOMExternalizableStringList myReadOrder;

  private static final Map<String, HighlightInfoType> STANDARD_SEVERITIES = new THashMap<String, HighlightInfoType>();

  static {
    STANDARD_SEVERITIES.put(HighlightSeverity.ERROR.toString(), HighlightInfoType.ERROR);
    STANDARD_SEVERITIES.put(HighlightSeverity.WARNING.toString(), HighlightInfoType.WARNING);
    STANDARD_SEVERITIES.put(HighlightSeverity.INFO.toString(), HighlightInfoType.INFO);
    STANDARD_SEVERITIES.put(HighlightSeverity.WEAK_WARNING.toString(), HighlightInfoType.WEAK_WARNING);
    STANDARD_SEVERITIES.put(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.toString(), HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER);
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType highlightInfoType : provider.getSeveritiesHighlightInfoTypes()) {
        final HighlightSeverity highlightSeverity = highlightInfoType.getSeverity(null);
        STANDARD_SEVERITIES.put(highlightSeverity.toString(), highlightInfoType);
        final TextAttributesKey attributesKey = highlightInfoType.getAttributesKey();
        TextAttributes textAttributes = scheme.getAttributes(attributesKey);
        if (textAttributes == null) {
          textAttributes = attributesKey.getDefaultAttributes();
        }
        HighlightDisplayLevel.registerSeverity(highlightSeverity, provider.getTrafficRendererColor(textAttributes));
      }
    }
  }

  public static SeverityRegistrar getInstance() {
    return InspectionProfileManager.getInstance().getSeverityRegistrar();
  }

  public static SeverityRegistrar getInstance(@Nullable Project project) {
    return project != null ? InspectionProjectProfileManager.getInstance(project).getSeverityRegistrar() : getInstance();
  }

  public void registerSeverity(SeverityBasedTextAttributes info, Color renderColor){
    final HighlightSeverity severity = info.getType().getSeverity(null);
    ourMap.put(severity.toString(), info);
    ourRendererColors.put(severity.toString(), renderColor);
    myOrder.clear();
    HighlightDisplayLevel.registerSeverity(severity, renderColor);
  }

  public Collection<SeverityBasedTextAttributes> getRegisteredHighlightingInfoTypes() {
    final Collection<SeverityBasedTextAttributes> collection = new ArrayList<SeverityBasedTextAttributes>(ourMap.values());
    for (HighlightInfoType type : STANDARD_SEVERITIES.values()) {
      collection.add(getSeverityBasedTextAttributes(type));
    }
    return collection;
  }

  private SeverityBasedTextAttributes getSeverityBasedTextAttributes(@NotNull HighlightInfoType type) {
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes textAttributes = scheme.getAttributes(type.getAttributesKey());
    if (textAttributes != null) {
      return new SeverityBasedTextAttributes(textAttributes, (HighlightInfoType.HighlightInfoTypeImpl)type);
    }
    return new SeverityBasedTextAttributes(getTextAttributesBySeverity(type.getSeverity(null)), (HighlightInfoType.HighlightInfoTypeImpl)type);
  }


  public SeverityBasedTextAttributes unregisterSeverity(HighlightSeverity severity){
    return ourMap.remove(severity.toString());
  }

  public HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(HighlightSeverity severity) {
    HighlightInfoType infoType = STANDARD_SEVERITIES.get(severity.toString());
    if (infoType != null) {
      return (HighlightInfoType.HighlightInfoTypeImpl)infoType;
    }

    if (severity == HighlightSeverity.INFORMATION){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFORMATION;
    }

    final SeverityBasedTextAttributes type = ourMap.get(severity.toString());
    return (HighlightInfoType.HighlightInfoTypeImpl)(type != null ? type.getType() : HighlightInfoType.WARNING);
  }

  @Nullable
  public TextAttributes getTextAttributesBySeverity(@NotNull HighlightSeverity severity) {
    final SeverityBasedTextAttributes infoType = ourMap.get(severity.toString());
    if (infoType != null) {
      return infoType.getAttributes();
    }
    return null;
  }


  @Override
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

    myReadOrder = new JDOMExternalizableStringList();
    myReadOrder.readExternal(element);
    for (int i = 0; i < myReadOrder.size(); i++) {
      String name = myReadOrder.get(i);
      HighlightSeverity severity = getSeverity(name);
      if (severity == null) continue;
      myOrder.put(severity, i);
    }
    final List<HighlightSeverity> knownSeverities = getDefaultOrder();
    myOrder.retainEntries(new TObjectIntProcedure<HighlightSeverity>() {
      @Override
      public boolean execute(HighlightSeverity severity, int order) {
        return knownSeverities.contains(severity);
      }
    });

    if (myOrder.isEmpty()) {
      setFromList(knownSeverities);
    }
    //enforce include all known
    List<HighlightSeverity> list = getOrderAsList();
    for (int i = 0; i < knownSeverities.size(); i++) {
      HighlightSeverity stdSeverity = knownSeverities.get(i);
      if (!list.contains(stdSeverity)) {
        for (int oIdx = 0; oIdx < list.size(); oIdx++) {
          HighlightSeverity orderSeverity = list.get(oIdx);
          HighlightInfoType type = STANDARD_SEVERITIES.get(orderSeverity.toString());
          if (type != null && knownSeverities.indexOf(type.getSeverity(null)) > i) {
            list.add(oIdx, stdSeverity);
            myReadOrder = null;
            break;
          }
        }
      }
    }
    setFromList(list);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    List<HighlightSeverity> list = getOrderAsList();
    for (HighlightSeverity s : list) {
      Element info = new Element(INFO);
      String severity = s.toString();
      final SeverityBasedTextAttributes infoType = ourMap.get(severity);
      if (infoType != null) {
        infoType.writeExternal(info);
        final Color color = ourRendererColors.get(severity);
        if (color != null) {
          info.setAttribute(COLOR, Integer.toString(color.getRGB() & 0xFFFFFF, 16));
        }
        element.addContent(info);
      }
    }

    if (myReadOrder != null && !myReadOrder.isEmpty()) {
      myReadOrder.writeExternal(element);
    }
    else if (!getDefaultOrder().equals(list)) {
      final JDOMExternalizableStringList ext = new JDOMExternalizableStringList(Collections.nCopies(myOrder.size(), ""));
      myOrder.forEachEntry(new TObjectIntProcedure<HighlightSeverity>() {
        @Override
        public boolean execute(HighlightSeverity orderSeverity, int oIdx) {
          ext.set(oIdx, orderSeverity.toString());
          return true;
        }
      });
      ext.writeExternal(element);
    }
  }

  @NotNull
  private List<HighlightSeverity> getOrderAsList() {
    List<HighlightSeverity> list = new ArrayList<HighlightSeverity>();
    for (Object o : getOrder().keys()) {
      list.add((HighlightSeverity)o);
    }
    Collections.sort(list, this);
    return list;
  }

  public int getSeveritiesCount() {
    return createCurrentSeverities().size();
  }

  public HighlightSeverity getSeverityByIndex(final int i) {
    final HighlightSeverity[] found = new HighlightSeverity[1];
    getOrder().forEachEntry(new TObjectIntProcedure<HighlightSeverity>() {
      @Override
      public boolean execute(HighlightSeverity severity, int order) {
        if (order == i) {
          found[0] = severity;
          return false;
        }
        return true;
      }
    });
    return found[0];
  }

  public int getSeverityMaxIndex() {
    int[] values = getOrder().getValues();
    int max = values[0];
    for(int i = 1; i < values.length; ++i) if (values[i] > max) max = values[i];

    return max;
  }

  @Nullable
  public HighlightSeverity getSeverity(@NotNull String name) {
    final HighlightInfoType type = STANDARD_SEVERITIES.get(name);
    if (type != null) return type.getSeverity(null);
    final SeverityBasedTextAttributes attributes = ourMap.get(name);
    if (attributes != null) return attributes.getSeverity();
    return null;
  }

  @NotNull
  private List<String> createCurrentSeverities() {
    List<String> list = new ArrayList<String>();
    list.addAll(STANDARD_SEVERITIES.keySet());
    list.addAll(ourMap.keySet());
    ContainerUtil.sort(list);
    return list;
  }

  public Icon getRendererIconByIndex(int i) {
    final HighlightSeverity severity = getSeverityByIndex(i);
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return level.getIcon();
    }

    return HighlightDisplayLevel.createIconByMask(ourRendererColors.get(severity.toString()));
  }

  public boolean isSeverityValid(@NotNull String severity) {
    return createCurrentSeverities().contains(severity);
  }

  @Override
  public int compare(final HighlightSeverity s1, final HighlightSeverity s2) {
    OrderMap order = getOrder();
    int o1 = order.getOrder(s1, -1);
    int o2 = order.getOrder(s2, -1);
    return o1 - o2;
  }


  @NotNull
  private OrderMap getOrder() {
    if (myOrder.isEmpty()) {
      List<HighlightSeverity> order = getDefaultOrder();
      setFromList(order);
    }
    return myOrder;
  }

  private void setFromList(@NotNull List<HighlightSeverity> order) {
    myOrder.clear();
    for (int i = 0; i < order.size(); i++) {
      HighlightSeverity severity = order.get(i);
      myOrder.put(severity, i);
    }
  }

  @NotNull
  private List<HighlightSeverity> getDefaultOrder() {
    Collection<SeverityBasedTextAttributes> values = ourMap.values();
    List<HighlightSeverity> order = new ArrayList<HighlightSeverity>(STANDARD_SEVERITIES.size() + values.size());
    for (HighlightInfoType type : STANDARD_SEVERITIES.values()) {
      order.add(type.getSeverity(null));
    }
    for (SeverityBasedTextAttributes attributes : values) {
      order.add(attributes.getSeverity());
    }
    ContainerUtil.sort(order);
    return order;
  }

  public void setOrder(@NotNull List<HighlightSeverity> order) {
    setFromList(order);
    myReadOrder = null;
  }

  public int getSeverityIdx(@NotNull HighlightSeverity severity) {
    return getOrder().getOrder(severity, -1);
  }

  public boolean isDefaultSeverity(@NotNull HighlightSeverity severity) {
    return STANDARD_SEVERITIES.containsKey(severity.myName);
  }

  public static boolean isGotoBySeverityEnabled(@NotNull HighlightSeverity minSeverity) {
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      if (provider.isGotoBySeverityEnabled(minSeverity)) return true;
    }
    return minSeverity != HighlightSeverity.INFORMATION;
  }

  public static class SeverityBasedTextAttributes implements JDOMExternalizable {
    private final TextAttributes myAttributes;
    private final HighlightInfoType.HighlightInfoTypeImpl myType;

    //read external
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

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      myAttributes.readExternal(element);
      myType.readExternal(element);
    }

    @Override
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
      int result = myAttributes != null ? myAttributes.hashCode() : 0;
      result = 31 * result + (myType != null ? myType.hashCode() : 0);
      return result;
    }
  }

  private static class OrderMap extends TObjectIntHashMap<HighlightSeverity> {
    private int getOrder(@NotNull HighlightSeverity severity, int defaultOrder) {
      int index = index(severity);
      return index < 0 ? defaultOrder : _values[index];
    }
  }
}
