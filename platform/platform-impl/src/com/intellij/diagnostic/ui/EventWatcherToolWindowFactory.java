// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.ui;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.diagnostic.RunnablesListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
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
    toolWindow.setStripeTitle(DiagnosticBundle.message("tab.title.event.watcher"));
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
          FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable.callable"), InvocationsInfo::getFQN),
          new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.average.duration.ms"), Double.TYPE, InvocationsInfo::getAverageDuration),
          new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.count"), Integer.TYPE, InvocationsInfo::getCount)
        },
        new ArrayList<>(),
        1,
        SortOrder.DESCENDING
      );

      myRunnablesModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable"), InvocationDescription::getProcessId),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.duration.ms"), Long.TYPE, InvocationDescription::getDuration),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.started.at"), String.class,
                                      description -> new SimpleDateFormat().format(new Date(description.getStartedAt())),
                                      Comparator.comparingLong(InvocationDescription::getStartedAt))
      );

      myWrappersModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable.callable"), WrapperDescription::getFQN),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.usages.count"), Integer.TYPE, WrapperDescription::getUsagesCount)
      );

      myAcquirementsModel = new ListTableModel<>(
        FunctionBasedColumnInfo.stringBased(DiagnosticBundle.message("event.watcher.column.name.runnable"), LockAcquirementDescription::getFQN),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.reads"), Long.TYPE, LockAcquirementDescription::getReads),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.writes"), Long.TYPE, LockAcquirementDescription::getWrites),
        new FunctionBasedColumnInfo<>(DiagnosticBundle.message("event.watcher.column.name.write.intents"), Long.TYPE, LockAcquirementDescription::getWriteIntents)
      );

      myContents = Arrays.asList(
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.invocations"), myInvocationsModel),
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.runnables"), myRunnablesModel),
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.wrappers"), myWrappersModel),
        createTableContent(DiagnosticBundle.message("event.watcher.tab.title.locks"), myAcquirementsModel)
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
    private static Content createTableContent(@NotNull @NlsContexts.TabTitle String tableName,
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

      private FunctionBasedColumnInfo(@NotNull @Nls String name,
                                      @NotNull Class<? extends Aspect> columnClass,
                                      @NotNull Function<? super Item, ? extends Aspect> extractor,
                                      @NotNull Comparator<Item> comparator) {
        super(name);
        myColumnClass = columnClass;
        myExtractor = extractor;
        myComparator = comparator;
      }

      private FunctionBasedColumnInfo(@NotNull @Nls String name,
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

      private static <Item extends Comparable<? super Item>> FunctionBasedColumnInfo<Item, String> stringBased(@NotNull @NlsContexts.ColumnName String name,
                                                                                                               @NotNull Function<? super Item, String> extractor) {
        return new FunctionBasedColumnInfo<Item, String>(name, String.class, extractor, Comparator.naturalOrder());
      }
    }
  }
}
