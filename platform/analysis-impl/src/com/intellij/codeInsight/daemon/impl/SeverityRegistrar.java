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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
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
  @NonNls private static final String INFO_TAG = "info";
  @NonNls private static final String COLOR_ATTRIBUTE = "color";
  private final Map<String, SeverityBasedTextAttributes> myMap = new ConcurrentHashMap<String, SeverityBasedTextAttributes>();
  private final Map<String, Color> myRendererColors = new ConcurrentHashMap<String, Color>();

  private volatile OrderMap myOrderMap;
  private JDOMExternalizableStringList myReadOrder;

  private static final Map<String, HighlightInfoType> STANDARD_SEVERITIES = new ConcurrentHashMap<String, HighlightInfoType>();

  public SeverityRegistrar() {
  }

  static {
    registerStandard(HighlightInfoType.ERROR, HighlightSeverity.ERROR);
    registerStandard(HighlightInfoType.WARNING, HighlightSeverity.WARNING);
    registerStandard(HighlightInfoType.INFO, HighlightSeverity.INFO);
    registerStandard(HighlightInfoType.WEAK_WARNING, HighlightSeverity.WEAK_WARNING);
    registerStandard(HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER, HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING);
  }

  public static void registerStandard(@NotNull HighlightInfoType highlightInfoType, @NotNull HighlightSeverity highlightSeverity) {
    STANDARD_SEVERITIES.put(highlightSeverity.getName(), highlightInfoType);
  }

  @NotNull
  public static SeverityRegistrar getSeverityRegistrar(@Nullable Project project) {
    return project == null
           ? InspectionProfileManager.getInstance().getSeverityRegistrar()
           : InspectionProjectProfileManager.getInstance(project).getSeverityRegistrar();
  }

  public void registerSeverity(@NotNull SeverityBasedTextAttributes info, Color renderColor) {
    final HighlightSeverity severity = info.getType().getSeverity(null);
    myMap.put(severity.getName(), info);
    myRendererColors.put(severity.getName(), renderColor);
    myOrderMap = null;
    HighlightDisplayLevel.registerSeverity(severity, renderColor);
  }

  public SeverityBasedTextAttributes unregisterSeverity(@NotNull HighlightSeverity severity){
    return myMap.remove(severity.getName());
  }

  @NotNull
  public HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(@NotNull HighlightSeverity severity) {
    HighlightInfoType infoType = STANDARD_SEVERITIES.get(severity.getName());
    if (infoType != null) {
      return (HighlightInfoType.HighlightInfoTypeImpl)infoType;
    }

    if (severity == HighlightSeverity.INFORMATION){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFORMATION;
    }

    final SeverityBasedTextAttributes type = getAttributesBySeverity(severity);
    return (HighlightInfoType.HighlightInfoTypeImpl)(type == null ? HighlightInfoType.WARNING : type.getType());
  }

  private SeverityBasedTextAttributes getAttributesBySeverity(@NotNull HighlightSeverity severity) {
    return myMap.get(severity.getName());
  }

  @Nullable
  public TextAttributes getTextAttributesBySeverity(@NotNull HighlightSeverity severity) {
    final SeverityBasedTextAttributes infoType = getAttributesBySeverity(severity);
    if (infoType != null) {
      return infoType.getAttributes();
    }
    return null;
  }


  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myMap.clear();
    myRendererColors.clear();
    final List children = element.getChildren(INFO_TAG);
    for (Object child : children) {
      final Element infoElement = (Element)child;

      final SeverityBasedTextAttributes highlightInfo = new SeverityBasedTextAttributes(infoElement);

      Color color = null;
      final String colorStr = infoElement.getAttributeValue(COLOR_ATTRIBUTE);
      if (colorStr != null){
        color = new Color(Integer.parseInt(colorStr, 16));
      }
      registerSeverity(highlightInfo, color);
    }
    myReadOrder = new JDOMExternalizableStringList();
    myReadOrder.readExternal(element);

    OrderMap orderMap = new OrderMap(myReadOrder.size());
    for (int i = 0; i < myReadOrder.size(); i++) {
      String name = myReadOrder.get(i);
      HighlightSeverity severity = getSeverity(name);
      if (severity == null) continue;
      orderMap.put(severity, i);
    }
    final List<HighlightSeverity> knownSeverities = getDefaultOrder();
    orderMap.retainEntries(new TObjectIntProcedure<HighlightSeverity>() {
      @Override
      public boolean execute(HighlightSeverity severity, int order) {
        return knownSeverities.contains(severity);
      }
    });

    if (orderMap.isEmpty()) {
      orderMap = fromList(knownSeverities);
    }
    else {
      //enforce include all known
      List<HighlightSeverity> list = getOrderAsList(orderMap);
      for (int i = 0; i < knownSeverities.size(); i++) {
        HighlightSeverity stdSeverity = knownSeverities.get(i);
        if (!list.contains(stdSeverity)) {
          for (int oIdx = 0; oIdx < list.size(); oIdx++) {
            HighlightSeverity orderSeverity = list.get(oIdx);
            HighlightInfoType type = STANDARD_SEVERITIES.get(orderSeverity.getName());
            if (type != null && knownSeverities.indexOf(type.getSeverity(null)) > i) {
              list.add(oIdx, stdSeverity);
              myReadOrder = null;
              break;
            }
          }
        }
      }
      orderMap = fromList(list);
    }
    myOrderMap = orderMap;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    List<HighlightSeverity> list = getOrderAsList(getOrderMap());
    for (HighlightSeverity severity : list) {
      Element info = new Element(INFO_TAG);
      String severityName = severity.getName();
      final SeverityBasedTextAttributes infoType = getAttributesBySeverity(severity);
      if (infoType != null) {
        infoType.writeExternal(info);
        final Color color = myRendererColors.get(severityName);
        if (color != null) {
          info.setAttribute(COLOR_ATTRIBUTE, Integer.toString(color.getRGB() & 0xFFFFFF, 16));
        }
        element.addContent(info);
      }
    }

    if (myReadOrder != null && !myReadOrder.isEmpty()) {
      myReadOrder.writeExternal(element);
    }
    else if (!getDefaultOrder().equals(list)) {
      final JDOMExternalizableStringList ext = new JDOMExternalizableStringList(Collections.nCopies(getOrderMap().size(), ""));
      getOrderMap().forEachEntry(new TObjectIntProcedure<HighlightSeverity>() {
        @Override
        public boolean execute(HighlightSeverity orderSeverity, int oIdx) {
          ext.set(oIdx, orderSeverity.getName());
          return true;
        }
      });
      ext.writeExternal(element);
    }
  }

  @NotNull
  private List<HighlightSeverity> getOrderAsList(@NotNull OrderMap orderMap) {
    List<HighlightSeverity> list = new ArrayList<HighlightSeverity>();
    for (Object o : orderMap.keys()) {
      list.add((HighlightSeverity)o);
    }
    Collections.sort(list, this);
    return list;
  }

  public int getSeveritiesCount() {
    return createCurrentSeverityNames().size();
  }

  public HighlightSeverity getSeverityByIndex(final int i) {
    final HighlightSeverity[] found = new HighlightSeverity[1];
    getOrderMap().forEachEntry(new TObjectIntProcedure<HighlightSeverity>() {
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
    int[] values = getOrderMap().getValues();
    int max = values[0];
    for(int i = 1; i < values.length; ++i) if (values[i] > max) max = values[i];

    return max;
  }

  @Nullable
  public HighlightSeverity getSeverity(@NotNull String name) {
    final HighlightInfoType type = STANDARD_SEVERITIES.get(name);
    if (type != null) return type.getSeverity(null);
    final SeverityBasedTextAttributes attributes = myMap.get(name);
    if (attributes != null) return attributes.getSeverity();
    return null;
  }

  @NotNull
  private List<String> createCurrentSeverityNames() {
    List<String> list = new ArrayList<String>();
    list.addAll(STANDARD_SEVERITIES.keySet());
    list.addAll(myMap.keySet());
    ContainerUtil.sort(list);
    return list;
  }

  public Icon getRendererIconByIndex(int i) {
    final HighlightSeverity severity = getSeverityByIndex(i);
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return level.getIcon();
    }

    return HighlightDisplayLevel.createIconByMask(myRendererColors.get(severity.getName()));
  }

  public boolean isSeverityValid(@NotNull String severityName) {
    return createCurrentSeverityNames().contains(severityName);
  }

  @Override
  public int compare(final HighlightSeverity s1, final HighlightSeverity s2) {
    OrderMap orderMap = getOrderMap();
    int o1 = orderMap.getOrder(s1, -1);
    int o2 = orderMap.getOrder(s2, -1);
    return o1 - o2;
  }


  @NotNull
  private OrderMap getOrderMap() {
    OrderMap orderMap;
    OrderMap defaultOrder = null;
    while ((orderMap = myOrderMap) == null) {
      if (defaultOrder == null) {
        defaultOrder = fromList(getDefaultOrder());
      }
      boolean replaced = ORDER_MAP_UPDATER.compareAndSet(this, null, defaultOrder);
      if (replaced) {
        orderMap = defaultOrder;
        break;
      }
    }
    return orderMap;
  }

  private static final AtomicFieldUpdater<SeverityRegistrar, OrderMap> ORDER_MAP_UPDATER = AtomicFieldUpdater.forFieldOfType(SeverityRegistrar.class, OrderMap.class);

  @NotNull
  private static OrderMap fromList(@NotNull List<HighlightSeverity> orderList) {
    OrderMap orderMap = new OrderMap(orderList.size());
    for (int i = 0; i < orderList.size(); i++) {
      HighlightSeverity severity = orderList.get(i);
      orderMap.put(severity, i);
    }
    orderMap.trimToSize();
    return orderMap;
  }

  @NotNull
  private List<HighlightSeverity> getDefaultOrder() {
    Collection<SeverityBasedTextAttributes> values = myMap.values();
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

  public void setOrder(@NotNull List<HighlightSeverity> orderList) {
    myOrderMap = fromList(orderList);
    myReadOrder = null;
  }

  public int getSeverityIdx(@NotNull HighlightSeverity severity) {
    return getOrderMap().getOrder(severity, -1);
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

  private static class OrderMap extends TObjectIntHashMap<HighlightSeverity> {
    private OrderMap(int initialCapacity) {
      super(initialCapacity);
    }

    private int getOrder(@NotNull HighlightSeverity severity, int defaultOrder) {
      int index = index(severity);
      return index < 0 ? defaultOrder : _values[index];
    }
  }

  public static class SeverityBasedTextAttributes {
    private final TextAttributes myAttributes;
    private final HighlightInfoType.HighlightInfoTypeImpl myType;

    //read external
    public SeverityBasedTextAttributes(@NotNull Element element) throws InvalidDataException {
      this(new TextAttributes(element), new HighlightInfoType.HighlightInfoTypeImpl(element));
    }

    public SeverityBasedTextAttributes(@NotNull TextAttributes attributes, @NotNull HighlightInfoType.HighlightInfoTypeImpl type) {
      myAttributes = attributes;
      myType = type;
    }

    @NotNull
    public TextAttributes getAttributes() {
      return myAttributes;
    }

    @NotNull
    public HighlightInfoType.HighlightInfoTypeImpl getType() {
      return myType;
    }

    private void writeExternal(@NotNull Element element) throws WriteExternalException {
      myAttributes.writeExternal(element);
      myType.writeExternal(element);
    }

    @NotNull
    public HighlightSeverity getSeverity() {
      return myType.getSeverity(null);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SeverityBasedTextAttributes that = (SeverityBasedTextAttributes)o;

      if (!myAttributes.equals(that.myAttributes)) return false;
      if (!myType.equals(that.myType)) return false;

      return true;
    }

    public int hashCode() {
      int result = myAttributes.hashCode();
      result = 31 * result + myType.hashCode();
      return result;
    }
  }

  @NotNull
  Collection<SeverityBasedTextAttributes> allRegisteredAttributes() {
    return new ArrayList<SeverityBasedTextAttributes>(myMap.values());
  }
  @NotNull
  Collection<HighlightInfoType> standardSeverities() {
    return STANDARD_SEVERITIES.values();
  }
}
