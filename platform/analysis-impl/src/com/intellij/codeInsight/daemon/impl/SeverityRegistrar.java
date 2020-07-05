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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import gnu.trove.TIntFunction;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

  private volatile OrderMap myOrderMap;
  private JDOMExternalizableStringList myReadOrder;

  private static final Map<String, HighlightInfoType> STANDARD_SEVERITIES;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public SeverityRegistrar(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    messageBus.simpleConnect().subscribe(STANDARD_SEVERITIES_CHANGED_TOPIC, () -> myOrderMap = null);
  }

  static {
    Map<String, HighlightInfoType> map = new HashMap<>();
    map.put(HighlightSeverity.ERROR.getName(), HighlightInfoType.ERROR);
    map.put(HighlightSeverity.WARNING.getName(), HighlightInfoType.WARNING);
    map.put(HighlightSeverity.INFO.getName(), HighlightInfoType.INFO);
    map.put(HighlightSeverity.WEAK_WARNING.getName(), HighlightInfoType.WEAK_WARNING);
    map.put(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.getName(), HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER);
    map.put(HighlightDisplayLevel.DO_NOT_SHOW.getName(), HighlightInfoType.INFORMATION);
    STANDARD_SEVERITIES = new ConcurrentHashMap<>(map);
  }

  @SuppressWarnings("unused")
  public static void registerStandard(@NotNull HighlightInfoType highlightInfoType, @NotNull HighlightSeverity highlightSeverity) {
    STANDARD_SEVERITIES.put(highlightSeverity.getName(), highlightInfoType);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(STANDARD_SEVERITIES_CHANGED_TOPIC).run();
  }

  public static void registerStandard(@NotNull Map<String, HighlightInfoType> map) {
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

  public void registerSeverity(@NotNull SeverityBasedTextAttributes info, Color renderColor) {
    HighlightSeverity severity = info.getType().getSeverity(null);
    myMap.put(severity.getName(), info);
    if (renderColor != null) {
      myRendererColors.put(severity.getName(), renderColor);
    }
    myOrderMap = null;
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
    myOrderMap = ensureAllStandardIncluded(read, knownSeverities);
    severitiesChanged();
  }

  private OrderMap ensureAllStandardIncluded(List<? extends HighlightSeverity> read, List<? extends HighlightSeverity> knownSeverities) {
    OrderMap orderMap = fromList(read);
    if (orderMap.isEmpty()) {
      orderMap = fromList(knownSeverities);
    }
    else {
      //enforce include all known
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
      orderMap = fromList(list);
    }
    return orderMap;
  }

  public void writeExternal(Element element) {
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

    if (myReadOrder != null && !myReadOrder.isEmpty()) {
      myReadOrder.writeExternal(element);
    }
    else if (!getDefaultOrder().equals(list)) {
      JDOMExternalizableStringList ext = new JDOMExternalizableStringList(Collections.nCopies(getOrderMap().size(), ""));
      getOrderMap().forEachEntry((orderSeverity, oIdx) -> {
        ext.set(oIdx, orderSeverity.getName());
        return true;
      });
      ext.writeExternal(element);
    }
  }

  public @NotNull List<HighlightSeverity> getAllSeverities() {
    return getSortedSeverities(getOrderMap());
  }

  private static @NotNull List<HighlightSeverity> getSortedSeverities(OrderMap map) {
    return Arrays.stream(map.keys())
                 .map(o -> (HighlightSeverity)o)
                 .sorted((o1, o2) -> compare(o1, o2, map))
                 .collect(Collectors.toList());
  }

  int getSeveritiesCount() {
    return STANDARD_SEVERITIES.size() + myMap.size();
  }

  public HighlightSeverity getSeverityByIndex(int i) {
    HighlightSeverity[] found = new HighlightSeverity[1];
    getOrderMap().forEachEntry((severity, order) -> {
      if (order == i) {
        found[0] = severity;
        return false;
      }
      return true;
    });
    return found[0];
  }

  int getSeverityMaxIndex() {
    return ArrayUtil.max(getOrderMap().getValues());
  }

  public @Nullable HighlightSeverity getSeverity(@NotNull String name) {
    HighlightInfoType type = STANDARD_SEVERITIES.get(name);
    if (type != null) return type.getSeverity(null);
    SeverityBasedTextAttributes attributes = myMap.get(name);
    if (attributes != null) return attributes.getSeverity();
    return null;
  }

  Icon getRendererIconByIndex(int i) {
    HighlightSeverity severity = getSeverityByIndex(i);
    HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
    if (level != null) {
      return level.getIcon();
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
                             @NotNull OrderMap orderMap) {
    int o1 = orderMap.getOrder(s1);
    int o2 = orderMap.getOrder(s2);
    return o1 - o2;
  }

  private @NotNull OrderMap getOrderMap() {
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

  private static @NotNull OrderMap fromList(@NotNull List<? extends HighlightSeverity> orderList) {
    if (orderList.size() != new HashSet<>(orderList).size()) {
      LOG.error("Severities order list MUST contain only unique severities: " + orderList);
    }
    TObjectIntHashMap<HighlightSeverity> map = new TObjectIntHashMap<>();
    for (int i = 0; i < orderList.size(); i++) {
      HighlightSeverity severity = orderList.get(i);
      map.put(severity, i);
    }
    return new OrderMap(map);
  }

  private @NotNull List<HighlightSeverity> getDefaultOrder() {
    Collection<SeverityBasedTextAttributes> values = myMap.values();
    List<HighlightSeverity> order = new ArrayList<>(STANDARD_SEVERITIES.size() + values.size());
    for (HighlightInfoType type : STANDARD_SEVERITIES.values()) {
      order.add(type.getSeverity(null));
    }
    for (SeverityBasedTextAttributes attributes : values) {
      order.add(attributes.getSeverity());
    }
    ContainerUtil.sort(order);
    return order;
  }

  public void setOrder(@NotNull List<? extends HighlightSeverity> orderList) {
    myOrderMap = ensureAllStandardIncluded(orderList, getDefaultOrder());
    myReadOrder = null;
    severitiesChanged();
  }

  int getSeverityIdx(@NotNull HighlightSeverity severity) {
    return getOrderMap().getOrder(severity);
  }

  public static boolean isDefaultSeverity(@NotNull HighlightSeverity severity) {
    return STANDARD_SEVERITIES.containsKey(severity.myName);
  }

  static boolean isGotoBySeverityEnabled(@NotNull HighlightSeverity minSeverity) {
    for (SeveritiesProvider provider : SeveritiesProvider.EP_NAME.getExtensionList()) {
      if (provider.isGotoBySeverityEnabled(minSeverity)) return true;
    }
    return minSeverity != HighlightSeverity.INFORMATION;
  }

  private static final class OrderMap extends TObjectIntHashMap<HighlightSeverity> {
    private OrderMap(@NotNull TObjectIntHashMap<? extends HighlightSeverity> map) {
      super(map.size());
      map.forEachEntry((key, value) -> {
        super.put(key, value);
        return true;
      });
      trimToSize();
    }

    private int getOrder(@NotNull HighlightSeverity severity) {
      int index = index(severity);
      return index < 0 ? -1 : _values[index];
    }


    @Override
    public void clear() {
      throw new IncorrectOperationException("readonly");
    }

    @Override
    protected void removeAt(int index) {
      throw new IncorrectOperationException("readonly");
    }

    @Override
    public void transformValues(TIntFunction function) {
      throw new IncorrectOperationException("readonly");
    }

    @Override
    public boolean adjustValue(HighlightSeverity key, int amount) {
      throw new IncorrectOperationException("readonly");
    }

    @Override
    public int put(HighlightSeverity key, int value) {
      throw new IncorrectOperationException("readonly");
    }

    @Override
    public int remove(HighlightSeverity key) {
      throw new IncorrectOperationException("readonly");
    }
  }

  public static class SeverityBasedTextAttributes {
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

      if (!myAttributes.equals(that.myAttributes)) return false;
      return myType.equals(that.myType);
    }

    @Override
    public int hashCode() {
      int result = myAttributes.hashCode();
      result = 31 * result + myType.hashCode();
      return result;
    }
  }

  @NotNull
  Collection<SeverityBasedTextAttributes> allRegisteredAttributes() {
    return Collections.unmodifiableCollection(myMap.values());
  }
  @NotNull
  Collection<HighlightInfoType> standardSeverities() {
    return STANDARD_SEVERITIES.values();
  }
}
