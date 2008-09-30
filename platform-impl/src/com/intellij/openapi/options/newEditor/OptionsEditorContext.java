package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.speedSearch.ElementFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class OptionsEditorContext {

  ElementFilter myFilter;

  CopyOnWriteArraySet<OptionsEditorColleague> myColleagues = new CopyOnWriteArraySet<OptionsEditorColleague>();

  Configurable myCurrentConfigurable;
  Set<Configurable> myModified = new CopyOnWriteArraySet<Configurable>();
  Map<Configurable, ConfigurationException> myErrors = new HashMap<Configurable, ConfigurationException>();

  public OptionsEditorContext(ElementFilter filter) {
    myFilter = filter;
  }

  void fireSelected(@Nullable final Configurable configurable, @NotNull OptionsEditorColleague requestor) {
    if (myCurrentConfigurable == configurable) return;

    final Configurable old = myCurrentConfigurable;
    myCurrentConfigurable = configurable;

    notify(new ColleagueAction() {
      public void process(final OptionsEditorColleague colleague) {
        colleague.onSelected(configurable, old);
      }
    }, requestor);

  }

  void fireModifiedAdded(@NotNull final Configurable configurable, OptionsEditorColleague requestor) {
    if (myModified.contains(configurable)) return;

    myModified.add(configurable);

    notify(new ColleagueAction() {
      public void process(final OptionsEditorColleague colleague) {
        colleague.onModifiedAdded(configurable);
      }
    }, requestor);

  }

  void fireModifiedRemoved(@NotNull final Configurable configurable, OptionsEditorColleague requestor) {
    if (!myModified.contains(configurable)) return;

    myModified.remove(configurable);

    notify(new ColleagueAction() {
      public void process(final OptionsEditorColleague colleague) {
        colleague.onModifiedRemoved(configurable);
      }
    }, requestor);
  }

  public void fireErrorsChanged(final Map<Configurable, ConfigurationException> errors, OptionsEditorColleague requestor) {
    if (myErrors.equals(errors)) return;

    myErrors = errors != null ? errors : new HashMap<Configurable, ConfigurationException>();

    notify(new ColleagueAction() {
      public void process(final OptionsEditorColleague colleague) {
        colleague.onErrorsChanged();
      }
    }, requestor);
  }

  void notify(ColleagueAction action, OptionsEditorColleague requestor) {
    for (Iterator<OptionsEditorColleague> iterator = myColleagues.iterator(); iterator.hasNext();) {
      OptionsEditorColleague each = iterator.next();
      if (each != requestor) {
        action.process(each);
      }
    }
  }


  interface ColleagueAction {
    void process(OptionsEditorColleague colleague);
  }


  @NotNull
  ElementFilter<Configurable> getFilter() {
    return myFilter;
  }

  public Configurable getCurrentConfigurable() {
    return myCurrentConfigurable;
  }

  public Set<Configurable> getModified() {
    return myModified;
  }

  public Map<Configurable, ConfigurationException> getErrors() {
    return myErrors;
  }

  public void addColleague(final OptionsEditorColleague colleague) {
    myColleagues.add(colleague);
  }


}