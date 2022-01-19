// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class SeverityRegistrar implements Comparator<HighlightSeverity>, ModificationTracker {
  /**
   * Always first {@link HighlightDisplayLevel#DO_NOT_SHOW} must be skipped during navigation, editing settings, etc.
   */
  static final int SHOWN_SEVERITIES_OFFSET = 1;

  private static final Logger LOG = Logger.getInstance(SeverityRegistrar.class);

  private static final Topic<Runnable> STANDARD_SEVERITIES_CHANGED_TOPIC = new Topic<>("standard severities changed", Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  @NonNls private static final String INFO_TAG = "info";
  @NonNls private static final String COLOR_ATTRIBUTE = "color";
  private final Map<String, SeverityBasedTextAttributes> myMap = new ConcurrentHashMap<>();
  private final Map<String, Color> myRendererColors = new ConcurrentHashMap<>();
  static final Topic<Runnable> SEVERITIES_CHANGED_TOPIC = new Topic<>("severities changed", Runnable.class, Topic.BroadcastDirection.TO_PARENT);
  private final @NotNull MessageBus myMessageBus;

  private final AtomicReference<Object2IntMap<HighlightSeverity>> orderMap = new AtomicReference<>();
  private JDOMExternalizableStringList myReadOrder;

  private static final Map<String, HighlightInfoType> STANDARD_SEVERITIES;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public SeverityRegistrar(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    messageBus.simpleConnect().subscribe(STANDARD_SEVERITIES_CHANGED_TOPIC, () -> orderMap.set(null));
  }

  static {
    Map<String, HighlightInfoType> map = new HashMap<>(6);
    map.put(HighlightSeverity.ERROR.getName(), HighlightInfoType.ERROR);
    map.put(HighlightSeverity.WARNING.getName(), HighlightInfoType.WARNING);
    map.put(HighlightSeverity.INFO.getName(), HighlightInfoType.INFO);
    map.put(HighlightSeverity.WEAK_WARNING.getName(), HighlightInfoType.WEAK_WARNING);
    map.put(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.getName(), HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER);
    map.put(HighlightDisplayLevel.DO_NOT_SHOW.getName(), HighlightInfoType.INFORMATION);
    STANDARD_SEVERITIES = new ConcurrentHashMap<>(map);
  }

  public static void registerStandard(@NotNull HighlightInfoType highlightInfoType, @NotNull HighlightSeverity highlightSeverity) {
    STANDARD_SEVERITIES.put(highlightSeverity.getName(), highlightInfoType);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(STANDARD_SEVERITIES_CHANGED_TOPIC).run();
  }

  public static void registerStandard(@NotNull Map<String, ? extends HighlightInfoType> map) {
    STANDARD_SEVERITIES.putAll(map);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(STANDARD_SEVERITIES_CHANGED_TOPIC).run();
  }

  public static @NotNull SeverityRegistrar getSeverityRegistrar(@Nullable Project project) {
    return project == null
           ? InspectionProfileManager.getInstance().getSeverityRegistrar()
           : InspectionProfileManager.getInstance(project).getCurrentProfile().getProfileManager().getSeverityRegistrar();
  }

  @Override
  public long getModificationCount() {
    return myModificationTracker.getModificationCount();
  }

  public void registerSeverity(@NotNull SeverityBasedTextAttributes info, @Nullable Color renderColor) {
    HighlightSeverity severity = info.getType().getSeverity(null);
    myMap.put(severity.getName(), info);
    if (renderColor != null) {
      myRendererColors.put(severity.getName(), renderColor);
    }
    orderMap.set(null);
    HighlightDisplayLevel.registerSeverity(severity, getHighlightInfoTypeBySeverity(severity).getAttributesKey(), null);
    severitiesChanged();
  }

  private void severitiesChanged() {
    myModificationTracker.incModificationCount();
    myMessageBus.syncPublisher(SEVERITIES_CHANGED_TOPIC).run();
  }

  // called only by SeverityEditorDialog and after that setOrder is called, so, severitiesChanged is not called here
  public SeverityBasedTextAttributes unregisterSeverity(@NotNull HighlightSeverity severity) {
    severitiesChanged();
    return myMap.remove(severity.getName());
  }

  public @NotNull HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(@NotNull HighlightSeverity severity) {
    HighlightInfoType infoType = STANDARD_SEVERITIES.get(severity.getName());
    if (infoType != null) {
      return (HighlightInfoType.HighlightInfoTypeImpl)infoType;
    }

    if (severity == HighlightSeverity.INFORMATION){
      return (HighlightInfoType.HighlightInfoTypeImpl)HighlightInfoType.INFORMATION;
    }

    SeverityBasedTextAttributes type = getAttributesBySeverity(severity);
    return (HighlightInfoType.HighlightInfoTypeImpl)(type == null ? HighlightInfoType.WARNING : type.getType());
  }

  private SeverityBasedTextAttributes getAttributesBySeverity(@NotNull HighlightSeverity severity) {
    return myMap.get(severity.getName());
  }

  public @Nullable TextAttributes getTextAttributesBySeverity(@NotNull HighlightSeverity severity) {
    SeverityBasedTextAttributes infoType = getAttributesBySeverity(severity);
    if (infoType != null) {
      return infoType.getAttributes();
    }
    return null;
  }


  public void readExternal(@NotNull Element element) {
    myMap.clear();
    myRendererColors.clear();
    for (Element infoElement : element.getChildren(INFO_TAG)) {
      SeverityBasedTextAttributes highlightInfo = new SeverityBasedTextAttributes(infoElement);
      String colorStr = infoElement.getAttributeValue(COLOR_ATTRIBUTE);
      @SuppressWarnings("UseJBColor")
      Color color = colorStr == null ? null : new Color(Integer.parseInt(colorStr, 16));
      registerSeverity(highlightInfo, color);
    }
    myReadOrder = new JDOMExternalizableStringList();
    myReadOrder.readExternal(element);
    List<HighlightSeverity> read = new ArrayList<>(myReadOrder.size());
    List<HighlightSeverity> knownSeverities = getDefaultOrder();
    for (String name : myReadOrder) {
      HighlightSeverity severity = getSeverity(name);
      if (severity != null && knownSeverities.contains(severity)) {
        read.add(severity);
      }
    }
    orderMap.set(ensureAllStandardIncluded(read, knownSeverities));
    severitiesChanged();
  }

  private @NotNull Object2IntMap<HighlightSeverity> ensureAllStandardIncluded(@NotNull List<? extends HighlightSeverity> read, @NotNull List<HighlightSeverity> knownSeverities) {
    Object2IntMap<HighlightSeverity> orderMap = fromList(read);
    if (orderMap.isEmpty()) {
      return fromList(knownSeverities);
    }

    // enforce include all known
    List<HighlightSeverity> list = getSortedSeverities(orderMap);
    for (HighlightSeverity stdSeverity : knownSeverities) {
      if (!list.contains(stdSeverity)) {
        for (int oIdx = 0; oIdx < list.size(); oIdx++) {
          HighlightSeverity orderSeverity = list.get(oIdx);
          if (orderSeverity.myVal > stdSeverity.myVal) {
            list.add(oIdx, stdSeverity);
            myReadOrder = null;
            break;
          }
        }
      }
    }
    return fromList(list);
  }

  public void writeExternal(@NotNull Element element) {
    List<HighlightSeverity> list = getAllSeverities();
    for (HighlightSeverity severity : list) {
      Element info = new Element(INFO_TAG);
      String severityName = severity.getName();
      SeverityBasedTextAttributes infoType = getAttributesBySeverity(severity);
      if (infoType != null) {
        infoType.writeExternal(info);
        Color color = myRendererColors.get(severityName);
        if (color != null) {
          info.setAttribute(COLOR_ATTRIBUTE, Integer.toString(color.getRGB() & 0xFFFFFF, 16));
        }
        element.addContent(info);
      }
    }


    //noinspection deprecation
    JDOMExternalizableStringList readOrder = myReadOrder;
    if (readOrder != null && !readOrder.isEmpty()) {
      readOrder.writeExternal(element);
    }
    else if (!getDefaultOrder().equals(list)) {
      Object2IntMap<HighlightSeverity> orderMap = getOrderMap();
      //noinspection deprecation
      JDOMExternalizableStringList ext = new JDOMExternalizableStringList(Collections.nCopies(orderMap.size(), ""));
      for (Object2IntMap.Entry<HighlightSeverity> entry : getOrderMap().object2IntEntrySet()) {
        ext.set(entry.getIntValue(), entry.getKey().getName());
      }
      ext.writeExternal(element);
    }
  }

  public @NotNull List<HighlightSeverity> getAllSeverities() {
    return getSortedSeverities(getOrderMap());
  }

  private static @NotNull List<HighlightSeverity> getSortedSeverities(@NotNull Object2IntMap<HighlightSeverity> map) {
    List<HighlightSeverity> list = new ArrayList<>(map.keySet());
    list.sort((o1, o2) -> compare(o1, o2, map));
    return list;
  }

  int getSeveritiesCount() {
    return STANDARD_SEVERITIES.size() + myMap.size();
  }

  public @Nullable HighlightSeverity getSeverityByIndex(int index) {
    for (Object2IntMap.Entry<HighlightSeverity> entry : getOrderMap().object2IntEntrySet()) {
      if (entry.getIntValue() == index) {
        return entry.getKey();
      }
    }
    return null;
  }

  public @Nullable HighlightSeverity getSeverity(@NotNull String name) {
    HighlightInfoType type = STANDARD_SEVERITIES.get(name);
    if (type != null) return type.getSeverity(null);
    SeverityBasedTextAttributes attributes = myMap.get(name);
    if (attributes != null) return attributes.getSeverity();
    return null;
  }

  @NotNull
  Icon getRendererIconBySeverity(@NotNull HighlightSeverity severity, boolean defaultIcon) {
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return defaultIcon ? level.getIcon() : level.getOutlineIcon();
    }

    return HighlightDisplayLevel.createIconByMask(myRendererColors.get(severity.getName()));
  }

  public boolean isSeverityValid(@NotNull String severityName) {
    return STANDARD_SEVERITIES.containsKey(severityName) || myMap.containsKey(severityName);
  }

  @Override
  public int compare(@NotNull HighlightSeverity s1, @NotNull HighlightSeverity s2) {
    return compare(s1, s2, getOrderMap());
  }

  private static int compare(@NotNull HighlightSeverity s1,
                             @NotNull HighlightSeverity s2,
                             @NotNull Object2IntMap<HighlightSeverity> orderMap) {
    return orderMap.getInt(s1) - orderMap.getInt(s2);
  }

  private @NotNull Object2IntMap<HighlightSeverity> getOrderMap() {
    Object2IntMap<HighlightSeverity> map = orderMap.get();
    if (map != null) return map;
    return orderMap.updateAndGet(oldMap -> oldMap == null ? fromList(getDefaultOrder()) : oldMap);
  }

  private static @NotNull Object2IntMap<HighlightSeverity> fromList(@NotNull List<? extends HighlightSeverity> orderList) {
    if (orderList.isEmpty()) {
      return Object2IntMaps.emptyMap();
    }

    Object2IntMap<HighlightSeverity> map = new Object2IntOpenHashMap<>(orderList.size());
    map.defaultReturnValue(-1);
    for (int index = 0; index < orderList.size(); index++) {
      HighlightSeverity severity = orderList.get(index);
      map.put(severity, index);
    }
    if (map.size() != orderList.size()) {
      LOG.error("Severities order list must contain unique severities but got: " + orderList);
    }
    return Object2IntMaps.unmodifiable(map);
  }

  private @NotNull List<HighlightSeverity> getDefaultOrder() {
    List<HighlightSeverity> order = new ArrayList<>(STANDARD_SEVERITIES.size() + myMap.size());
    for (HighlightInfoType type : STANDARD_SEVERITIES.values()) {
      order.add(type.getSeverity(null));
    }
    for (SeverityBasedTextAttributes attributes : myMap.values()) {
      order.add(attributes.getSeverity());
    }
    order.sort(null);
    return order;
  }

  public void setOrder(@NotNull List<? extends HighlightSeverity> orderList) {
    orderMap.set(ensureAllStandardIncluded(orderList, getDefaultOrder()));
    myReadOrder = null;
    severitiesChanged();
  }

  int getSeverityIdx(@NotNull HighlightSeverity severity) {
    return getOrderMap().getInt(severity);
  }

  public static boolean isDefaultSeverity(@NotNull HighlightSeverity severity) {
    return STANDARD_SEVERITIES.containsKey(severity.myName);
  }

  static boolean isGotoBySeverityEnabled(@NotNull HighlightSeverity minSeverity) {
    for (SeveritiesProvider provider : SeveritiesProvider.EP_NAME.getIterable()) {
      if (provider.isGotoBySeverityEnabled(minSeverity)) {
        return true;
      }
    }
    return minSeverity != HighlightSeverity.INFORMATION;
  }

  public static final class SeverityBasedTextAttributes {
    private final TextAttributes myAttributes;
    private final HighlightInfoType.HighlightInfoTypeImpl myType;

    //read external
    SeverityBasedTextAttributes(@NotNull Element element)  {
      this(new TextAttributes(element), new HighlightInfoType.HighlightInfoTypeImpl(element));
    }

    public SeverityBasedTextAttributes(@NotNull TextAttributes attributes, @NotNull HighlightInfoType.HighlightInfoTypeImpl type) {
      myAttributes = attributes;
      myType = type;
    }

    public @NotNull TextAttributes getAttributes() {
      return myAttributes;
    }

    public @NotNull HighlightInfoType.HighlightInfoTypeImpl getType() {
      return myType;
    }

    private void writeExternal(@NotNull Element element) {
      myAttributes.writeExternal(element);
      myType.writeExternal(element);
    }

    public @NotNull HighlightSeverity getSeverity() {
      return myType.getSeverity(null);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SeverityBasedTextAttributes that = (SeverityBasedTextAttributes)o;
      return myAttributes.equals(that.myAttributes) && myType.equals(that.myType);
    }

    @Override
    public int hashCode() {
      return 31 * myAttributes.hashCode() + myType.hashCode();
    }
  }

  @NotNull
  Collection<@NotNull SeverityBasedTextAttributes> allRegisteredAttributes() {
    return Collections.unmodifiableCollection(myMap.values());
  }
  @NotNull
  static Collection<HighlightInfoType> standardSeverities() {
    return STANDARD_SEVERITIES.values();
  }
}
