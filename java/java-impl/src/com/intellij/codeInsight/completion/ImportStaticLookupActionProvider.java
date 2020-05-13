package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.java.JavaBundle;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;

/**
 * @author peter
 */
public class ImportStaticLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(final LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    final StaticallyImportable item = element.as(StaticallyImportable.CLASS_CONDITION_KEY);
    if (item == null || !item.canBeImported()) {
      return;
    }

    final Icon checkIcon = PlatformIcons.CHECK_ICON;
    final Icon icon = item.willBeImported() ? checkIcon : EmptyIcon.create(checkIcon);
    consumer.consume(new LookupElementAction(icon, JavaBundle.message("import.statically")) {
      @Override
      public Result performLookupAction() {
        item.setShouldBeImported(!item.willBeImported());
        return new Result.ChooseItem(element);
      }
    });
  }
}
