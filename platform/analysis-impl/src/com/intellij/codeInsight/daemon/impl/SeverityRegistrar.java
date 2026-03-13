// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class SeverityRegistrar implements Comparator<HighlightSeverity>, ModificationTracker {
  /**
   * Always first {@link HighlightDisplayLevel#DO_NOT_SHOW} must be skipped during navigation, editing settings, etc.
   */
  @Internal
  public static final int SHOWN_SEVERITIES_OFFSET = 2;

  @Topic.AppLevel
  @Topic.ProjectLevel
  private static final Topic<Runnable> STANDARD_SEVERITIES_CHANGED_TOPIC = new Topic<>("standard severities changed", Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  private static final @NonNls String INFO_TAG = "info";
  private static final @NonNls String COLOR_ATTRIBUTE = "color";
  private final Map<String, SeverityBasedTextAttributes> myMap = new ConcurrentHashMap<>();
  private final Map<String, Color> myRendererColors = new ConcurrentHashMap<>();

  @Topic.ProjectLevel
  @Internal
  public static final Topic<Runnable> SEVERITIES_CHANGED_TOPIC = new Topic<>("severities changed", Runnable.class, Topic.BroadcastDirection.TO_PARENT);
  private final @NotNull MessageBus myMessageBus;

  private final AtomicReference<Object2IntMap<HighlightSeverity>> orderMap = new AtomicReference<>();
  private JDOMExternalizableStringList myReadOrder;

  private static final Map<String, HighlightInfoType> CORE_STANDARD_SEVERITIES;
  private static final Map<String, HighlightInfoType> REGISTERED_STANDARD_SEVERITIES = new LinkedHashMap<>();
  private static final Object STANDARD_SEVERITIES_LOCK = new Object();
  private static volatile StandardSeveritiesState ourStandardSeveritiesState;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public SeverityRegistrar(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    messageBus.simpleConnect().subscribe(STANDARD_SEVERITIES_CHANGED_TOPIC, this::standardSeveritiesChanged);
  }

  static {
    Map<String, HighlightInfoType> map = new LinkedHashMap<>(7);
    map.put(HighlightDisplayLevel.DO_NOT_SHOW.getName(), HighlightInfoType.INFORMATION);
    map.put(HighlightDisplayLevel.CONSIDERATION_ATTRIBUTES.getName(), HighlightInfoType.TEXT_ATTRIBUTES);
    map.put(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.getName(), HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER);
    map.put(HighlightSeverity.INFO.getName(), HighlightInfoType.INFO);
    map.put(HighlightSeverity.WEAK_WARNING.getName(), HighlightInfoType.WEAK_WARNING);
    map.put(HighlightSeverity.WARNING.getName(), HighlightInfoType.WARNING);
    map.put(HighlightSeverity.ERROR.getName(), HighlightInfoType.ERROR);
    CORE_STANDARD_SEVERITIES = Collections.unmodifiableMap(map);
    ourStandardSeveritiesState = buildStandardSeveritiesState(Collections.emptyMap());
  }

  @SuppressWarnings("unused")
  public static void registerStandard(@NotNull HighlightInfoType highlightInfoType, @NotNull HighlightSeverity highlightSeverity) {
    Map<String, HighlightInfoType> map = new LinkedHashMap<>(1);
    map.put(highlightSeverity.getName(), highlightInfoType);
    boolean changed = false;
    synchronized (STANDARD_SEVERITIES_LOCK) {
      for (Map.Entry<String, ? extends HighlightInfoType> entry : ((Map<String, ? extends HighlightInfoType>)map).entrySet()) {
        HighlightInfoType previous = REGISTERED_STANDARD_SEVERITIES.put(entry.getKey(), entry.getValue());
        if (!entry.getValue().equals(previous)) {
          changed = true;
        }
      }
      if (changed) {
        ourStandardSeveritiesState = buildStandardSeveritiesState(getStandardSeveritiesState().providerTypes);
      }
    }

    if (changed) {
      publishStandardSeveritiesChanged();
    }
  }

  @Internal
  public static @NotNull Collection<String> syncProvidedSeverities(@NotNull Map<String, ? extends HighlightInfoType> map) {
    return doSyncProvidedSeverities(map, true);
  }

  @Internal
  public static @NotNull Collection<String> syncProvidedSeveritiesSilently(@NotNull Map<String, ? extends HighlightInfoType> map) {
    return doSyncProvidedSeverities(map, false);
  }

  private static @NotNull Collection<String> doSyncProvidedSeverities(@NotNull Map<String, ? extends HighlightInfoType> map,
                                                                      boolean notifyListeners) {
    LinkedHashMap<String, HighlightInfoType> providedTypes = new LinkedHashMap<>(map.size());
    providedTypes.putAll(map);

    Collection<String> removedNames = List.of();
    boolean changed = false;
    synchronized (STANDARD_SEVERITIES_LOCK) {
      StandardSeveritiesState oldState = getStandardSeveritiesState();
      if (!sameOrderedMap(oldState.providerTypes, providedTypes)) {
        StandardSeveritiesState newState = buildStandardSeveritiesState(providedTypes);
        LinkedHashSet<String> removed = new LinkedHashSet<>(oldState.allTypes.keySet());
        removed.removeAll(newState.allTypes.keySet());
        removedNames = removed;
        ourStandardSeveritiesState = newState;
        changed = true;
      }
    }

    if (changed && notifyListeners) {
      publishStandardSeveritiesChanged();
    }
    return removedNames;
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

  private void standardSeveritiesChanged() {
    List<String> orderNames = getOrderNames();
    if (orderNames == null) {
      orderMap.set(null);
    }
    else {
      orderMap.set(fromList(mergeOrderWithDefault(orderNames, getDefaultOrder())));
    }
    myReadOrder = null;
    severitiesChanged();
  }

  // called only by SeverityEditorDialog and after that setOrder is called, so, severitiesChanged is not called here
  public SeverityBasedTextAttributes unregisterSeverity(@NotNull HighlightSeverity severity) {
    severitiesChanged();
    return myMap.remove(severity.getName());
  }

  public @NotNull HighlightInfoType.HighlightInfoTypeImpl getHighlightInfoTypeBySeverity(@NotNull HighlightSeverity severity) {
    HighlightInfoType infoType = getStandardSeveritiesState().allTypes.get(severity.getName());
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

  public @Nullable TextAttributes getCustomSeverityTextAttributes(@NotNull TextAttributesKey key) {
    SeverityBasedTextAttributes attributes = myMap.get(key.getExternalName());
    return attributes != null ? attributes.getAttributes() : null;
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

  private @NotNull Object2IntMap<HighlightSeverity> ensureAllStandardIncluded(@NotNull List<HighlightSeverity> read, @NotNull List<HighlightSeverity> knownSeverities) {
    if (read.isEmpty()) {
      return fromList(knownSeverities);
    }

    List<String> readOrderNames = new ArrayList<>(read.size());
    for (HighlightSeverity severity : read) {
      readOrderNames.add(severity.getName());
    }

    List<HighlightSeverity> mergedOrder = mergeOrderWithDefault(readOrderNames, knownSeverities);
    if (!mergedOrder.equals(read)) {
      myReadOrder = null;
    }
    return fromList(mergedOrder);
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

  @Internal
  public int getSeveritiesCount() {
    return getStandardSeveritiesState().allTypes.size() + myMap.size();
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
    HighlightInfoType type = getStandardSeveritiesState().allTypes.get(name);
    if (type != null) return type.getSeverity(null);
    SeverityBasedTextAttributes attributes = myMap.get(name);
    if (attributes != null) return attributes.getSeverity();
    return null;
  }

  @NotNull
  @Internal
  public Icon getRendererIconBySeverity(@NotNull HighlightSeverity severity, boolean defaultIcon) {
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return defaultIcon ? level.getIcon() : level.getOutlineIcon();
    }

    return HighlightDisplayLevel.createIconByMask(myRendererColors.get(severity.getName()));
  }

  public boolean isSeverityValid(@NotNull String severityName) {
    return getStandardSeveritiesState().allTypes.containsKey(severityName) || myMap.containsKey(severityName);
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

  private static @NotNull Object2IntMap<HighlightSeverity> fromList(@NotNull List<HighlightSeverity> orderList) {
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
      Logger.getInstance(SeverityRegistrar.class).error("Severities order list must contain unique severities but got: " + orderList);
    }
    return Object2IntMaps.unmodifiable(map);
  }

  private @NotNull List<HighlightSeverity> getDefaultOrder() {
    List<HighlightSeverity> order = new ArrayList<>(getStandardSeveritiesState().defaultOrder.size() + myMap.size());
    order.addAll(getStandardSeveritiesState().defaultOrder);
    List<HighlightSeverity> customOrder = new ArrayList<>(myMap.size());
    for (SeverityBasedTextAttributes attributes : myMap.values()) {
      customOrder.add(attributes.getSeverity());
    }
    customOrder.sort(Comparator.comparing(HighlightSeverity::getName));
    order.addAll(customOrder);
    order.sort(null);
    return order;
  }

  public void setOrder(@NotNull List<HighlightSeverity> orderList) {
    orderMap.set(ensureAllStandardIncluded(orderList, getDefaultOrder()));
    myReadOrder = null;
    severitiesChanged();
  }

  @Internal
  public int getSeverityIdx(@NotNull HighlightSeverity severity) {
    return getOrderMap().getInt(severity);
  }

  public static boolean isDefaultSeverity(@NotNull HighlightSeverity severity) {
    return getStandardSeveritiesState().allTypes.containsKey(severity.myName);
  }

  @Internal
  public static boolean isGotoBySeverityEnabled(@NotNull HighlightSeverity minSeverity) {
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
  @Internal
  public Collection<@NotNull SeverityBasedTextAttributes> allRegisteredAttributes() {
    return Collections.unmodifiableCollection(myMap.values());
  }

  public static @NotNull Collection<HighlightInfoType> standardSeverities() {
    return getStandardSeveritiesState().orderedTypes;
  }

  private @Nullable List<String> getOrderNames() {
    Object2IntMap<HighlightSeverity> currentOrder = orderMap.get();
    if (currentOrder != null) {
      List<HighlightSeverity> severities = getSortedSeverities(currentOrder);
      List<String> names = new ArrayList<>(severities.size());
      for (HighlightSeverity severity : severities) {
        names.add(severity.getName());
      }
      return names;
    }

    JDOMExternalizableStringList readOrder = myReadOrder;
    if (readOrder != null && !readOrder.isEmpty()) {
      return new ArrayList<>(readOrder);
    }
    return null;
  }

  private static @NotNull List<HighlightSeverity> mergeOrderWithDefault(@NotNull List<String> currentOrderNames,
                                                                        @NotNull List<HighlightSeverity> defaultOrder) {
    LinkedHashMap<String, HighlightSeverity> severitiesByName = new LinkedHashMap<>(defaultOrder.size());
    LinkedHashMap<String, Integer> defaultIndices = new LinkedHashMap<>(defaultOrder.size());
    for (int i = 0; i < defaultOrder.size(); i++) {
      HighlightSeverity severity = defaultOrder.get(i);
      severitiesByName.put(severity.getName(), severity);
      defaultIndices.put(severity.getName(), i);
    }

    List<HighlightSeverity> result = new ArrayList<>(defaultOrder.size());
    for (String name : currentOrderNames) {
      HighlightSeverity severity = severitiesByName.remove(name);
      if (severity != null) {
        result.add(severity);
      }
    }

    for (HighlightSeverity severity : severitiesByName.values()) {
      insertByDefaultOrder(result, severity, defaultIndices);
    }
    return result;
  }

  private static void insertByDefaultOrder(@NotNull List<HighlightSeverity> result,
                                           @NotNull HighlightSeverity severity,
                                           @NotNull Map<String, Integer> defaultIndices) {
    Integer newSeverityIndex = defaultIndices.get(severity.getName());
    if (newSeverityIndex == null) {
      result.add(severity);
      return;
    }

    for (int i = 0; i < result.size(); i++) {
      Integer existingIndex = defaultIndices.get(result.get(i).getName());
      if (existingIndex != null && existingIndex > newSeverityIndex) {
        result.add(i, severity);
        return;
      }
    }
    result.add(severity);
  }

  private static boolean sameOrderedMap(@NotNull Map<String, ? extends HighlightInfoType> first,
                                        @NotNull Map<String, ? extends HighlightInfoType> second) {
    if (first.size() != second.size()) {
      return false;
    }

    var firstIterator = first.entrySet().iterator();
    var secondIterator = second.entrySet().iterator();
    while (firstIterator.hasNext() && secondIterator.hasNext()) {
      Map.Entry<String, ? extends HighlightInfoType> firstEntry = firstIterator.next();
      Map.Entry<String, ? extends HighlightInfoType> secondEntry = secondIterator.next();
      if (!firstEntry.getKey().equals(secondEntry.getKey()) || !firstEntry.getValue().equals(secondEntry.getValue())) {
        return false;
      }
    }
    return !firstIterator.hasNext() && !secondIterator.hasNext();
  }

  private static @NotNull StandardSeveritiesState buildStandardSeveritiesState(@NotNull Map<String, ? extends HighlightInfoType> providerTypes) {
    LinkedHashMap<String, HighlightInfoType> effectiveTypes = new LinkedHashMap<>(CORE_STANDARD_SEVERITIES.size() + REGISTERED_STANDARD_SEVERITIES.size() + providerTypes.size());
    effectiveTypes.putAll(CORE_STANDARD_SEVERITIES);
    effectiveTypes.putAll(REGISTERED_STANDARD_SEVERITIES);
    effectiveTypes.putAll(providerTypes);

    List<HighlightInfoType> orderedTypes = List.copyOf(effectiveTypes.values());
    List<HighlightSeverity> defaultOrder = new ArrayList<>(orderedTypes.size());
    for (HighlightInfoType type : orderedTypes) {
      defaultOrder.add(type.getSeverity(null));
    }
    defaultOrder.sort(null);

    LinkedHashMap<String, HighlightInfoType> providerCopy = new LinkedHashMap<>(providerTypes.size());
    providerCopy.putAll(providerTypes);
    return new StandardSeveritiesState(
      Collections.unmodifiableMap(effectiveTypes),
      Collections.unmodifiableMap(providerCopy),
      List.copyOf(orderedTypes),
      List.copyOf(defaultOrder)
    );
  }

  private static @NotNull StandardSeveritiesState getStandardSeveritiesState() {
    return ourStandardSeveritiesState;
  }

  private static void publishStandardSeveritiesChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(STANDARD_SEVERITIES_CHANGED_TOPIC).run();
  }

  private static final class StandardSeveritiesState {
    private final @NotNull Map<String, HighlightInfoType> allTypes;
    private final @NotNull Map<String, HighlightInfoType> providerTypes;
    private final @NotNull List<HighlightInfoType> orderedTypes;
    private final @NotNull List<HighlightSeverity> defaultOrder;

    private StandardSeveritiesState(@NotNull Map<String, HighlightInfoType> allTypes,
                                    @NotNull Map<String, HighlightInfoType> providerTypes,
                                    @NotNull List<HighlightInfoType> orderedTypes,
                                    @NotNull List<HighlightSeverity> defaultOrder) {
      this.allTypes = allTypes;
      this.providerTypes = providerTypes;
      this.orderedTypes = orderedTypes;
      this.defaultOrder = defaultOrder;
    }
  }
}
