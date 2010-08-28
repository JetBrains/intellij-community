package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;

/**
 * @author peter
 */
public class ImportStaticLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(final LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (!(element instanceof JavaGlobalMemberLookupElement)) {
      return;
    }

    final JavaGlobalMemberLookupElement item = (JavaGlobalMemberLookupElement)element;
    final Icon checkIcon = Icons.CHECK_ICON;
    final Icon icon = item.isShouldImport() ? checkIcon : new EmptyIcon(checkIcon.getIconWidth(), checkIcon.getIconHeight());
    consumer.consume(new LookupElementAction(icon, "Import statically") {
      @Override
      public Result performLookupAction() {
        item.setShouldImport(!item.isShouldImport());
        return Result.REFRESH_ITEM;
      }
    });
  }
}
