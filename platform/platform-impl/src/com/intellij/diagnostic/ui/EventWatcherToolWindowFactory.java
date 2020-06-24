// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.ui;

import com.intellij.diagnostic.EventWatcher;
import com.intellij.diagnostic.RunnablesListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;

@ApiStatus.Experimental
public final class EventWatcherToolWindowFactory implements ToolWindowFactory, DumbAware {

  @NotNull
  public static final String TOOL_WINDOW_ID = "Event Watcher";

  @Override
  public void createToolWindowContent(@NotNull Project project,
                                      @NotNull ToolWindow toolWindow) {
    TableProvidingListener listener = new TableProvidingListener();

    project.getMessageBus()
      .connect(project)
      .subscribe(listener.TOPIC, listener);

    listener.getContents()
      .forEach(toolWindow.getContentManager()::addContent);
  }

  @Override
  public void init(@NotNull ToolWindow toolWindow) {
    toolWindow.setStripeTitle(TOOL_WINDOW_ID);
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return EventWatcher.isEnabled();
  }

  private static class TableProvidingListener implements RunnablesListener {

    private final @NotNull ListTableModel<InvocationsInfo> myInvocationsModel;
    private final @NotNull ListTableModel<InvocationDescription> myRunnablesModel;
    private final @NotNull ListTableModel<WrapperDescription> myWrappersModel;
    private final @NotNull ListTableModel<LockAcquirementDescription> myAcquirementsModel;

    @NotNull
    private final List<Content> myContents;

    TableProvidingListener() {
      myInvocationsModel = new ListTableModel<>(
        new ColumnInfo[]{
          FunctionBasedColumnInfo.stringBased("Runnable/Callable", InvocationsInfo::getFQN),
          new FunctionBasedColumnInfo<>("Average duration, ms", Double.TYPE, InvocationsInfo::getAverageDuration),
          new FunctionBasedColumnInfo<>("Count", Integer.TYPE, InvocationsInfo::getCount)
        },
        new ArrayList<>(),
        1,
        SortOrder.DESCENDING
      );

      myRunnablesModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased("Runnable", InvocationDescription::getProcessId),
        new FunctionBasedColumnInfo<>("Duration, ms", Long.TYPE, InvocationDescription::getDuration),
        new FunctionBasedColumnInfo<>("Started at", String.class,
                                      description -> new SimpleDateFormat().format(new Date(description.getStartedAt())),
                                      Comparator.comparingLong(InvocationDescription::getStartedAt))
      );

      myWrappersModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased("Runnable/Callable", WrapperDescription::getFQN),
        new FunctionBasedColumnInfo<>("Usages count", Integer.TYPE, WrapperDescription::getUsagesCount)
      );

      myAcquirementsModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased("Runnable", LockAcquirementDescription::getFQN),
        new FunctionBasedColumnInfo<>("Reads", Long.TYPE, LockAcquirementDescription::getReads),
        new FunctionBasedColumnInfo<>("Writes", Long.TYPE, LockAcquirementDescription::getWrites),
        new FunctionBasedColumnInfo<>("Write intents", Long.TYPE, LockAcquirementDescription::getWriteIntents)
      );

      myContents = Arrays.asList(
        createTableContent("Invocations", myInvocationsModel),
        createTableContent("Runnables", myRunnablesModel),
        createTableContent("Wrappers", myWrappersModel),
        createTableContent("Locks", myAcquirementsModel)
      );
    }

    @Override
    public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                   @NotNull Collection<InvocationsInfo> infos,
                                   @NotNull Collection<WrapperDescription> wrappers) {
      myRunnablesModel.addRows(invocations);
      myInvocationsModel.setItems(new ArrayList<>(infos));
      myWrappersModel.setItems(new ArrayList<>(wrappers));
    }

    @Override
    public void locksAcquired(@NotNull Collection<LockAcquirementDescription> acquirements) {
      myAcquirementsModel.setItems(new ArrayList<>(acquirements));
    }

    @NotNull
    List<Content> getContents() {
      return myContents;
    }

    @NotNull
    private static Content createTableContent(@NotNull String tableName,
                                              @NotNull ListTableModel<?> tableModel) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(
        new JBScrollPane(new TableView<>(tableModel)),
        BorderLayout.CENTER
      );

      return ContentFactory.SERVICE
        .getInstance()
        .createContent(panel, tableName, false);
    }

    private static final class FunctionBasedColumnInfo<Item extends Comparable<? super Item>, Aspect extends Comparable<? super Aspect>>
      extends ColumnInfo<Item, Aspect> {

      @NotNull
      private final Class<? extends Aspect> myColumnClass;
      @NotNull
      private final Function<? super Item, ? extends Aspect> myExtractor;
      @NotNull
      private final Comparator<Item> myComparator;

      private FunctionBasedColumnInfo(@NotNull String name,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor,
                                      @NotNull Comparator<Item> comparator) {
        super(name);
        myColumnClass = columnClass;
        myExtractor = extractor;
        myComparator = comparator;
      }

      private FunctionBasedColumnInfo(@NotNull String name,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor) {
        this(name, columnClass, extractor, Comparator.comparing(extractor));
      }

      @Nullable
      @Override
      public final Aspect valueOf(@NotNull Item item) {
        return myExtractor.apply(item);
      }

      @NotNull
      @Override
      public final Class<? extends Aspect> getColumnClass() {
        return myColumnClass;
      }

      @NotNull
      @Override
      public final Comparator<Item> getComparator() {
        return myComparator;
      }

      private static <Item extends Comparable<? super Item>> FunctionBasedColumnInfo<Item, String> stringBased(@NotNull String name,
                                                                                                               @NotNull Function<? super Item, String> extractor) {
        return new FunctionBasedColumnInfo<Item, String>(name, String.class, extractor, Comparator.naturalOrder());
      }
    }
  }
}
