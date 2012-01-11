/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MergeableLineMarkerInfo<T extends PsiElement> extends LineMarkerInfo<T> {

  public MergeableLineMarkerInfo(@NotNull T element,
                                 TextRange textRange,
                                 Icon icon,
                                 int updatePass,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, updatePass, tooltipProvider, navHandler, alignment);
  }
  
  public abstract boolean canMergeWith(MergeableLineMarkerInfo<?> info);
  
  public abstract Icon getCommonIcon(List<MergeableLineMarkerInfo> infos);
  
  public static List<LineMarkerInfo> merge(List<MergeableLineMarkerInfo> markers) {
    final List<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>();
    final HashSet<LineMarkerInfo> processed = new HashSet<LineMarkerInfo>();
    for (MergeableLineMarkerInfo marker : markers) {
      if (processed.contains(marker)) continue;
      processed.add(marker);
      final List<MergeableLineMarkerInfo> toMerge = new ArrayList<MergeableLineMarkerInfo>();
      for (MergeableLineMarkerInfo current : markers) {
        if (!processed.contains(current) && marker.canMergeWith(current)) {
          toMerge.add(current);
          processed.add(current);
        }
      }
      if (toMerge.isEmpty()) {
        result.add(marker);
      } else {
        toMerge.add(marker);
        result.add(new MyLineMarkerInfo(toMerge));
      }
    }
    return result;
  }

  private static class MyLineMarkerInfo extends LineMarkerInfo {
    public MyLineMarkerInfo(List<MergeableLineMarkerInfo> markers) {
      //noinspection unchecked,ConstantConditions
      super(markers.get(0).getElement(),
            getCommonTextRange(markers),
            markers.get(0).getCommonIcon(markers),
            4, //TODO move Pass to lang-api and make it enum
            FunctionUtil.<Object, String>nullConstant(),
            getCommonNavigationHandler(markers),
            GutterIconRenderer.Alignment.RIGHT);
    }

    private static TextRange getCommonTextRange(List<MergeableLineMarkerInfo> markers) {
      int startOffset = Integer.MAX_VALUE;
      int endOffset = Integer.MIN_VALUE;
      for (MergeableLineMarkerInfo marker : markers) {
        startOffset = Math.min(startOffset, marker.startOffset);
        endOffset = Math.max(endOffset, marker.endOffset);
      }
      return TextRange.create(startOffset, endOffset);
    }

    private static GutterIconNavigationHandler getCommonNavigationHandler(final List<MergeableLineMarkerInfo> markers) {
      return new GutterIconNavigationHandler() {
        @Override
        public void navigate(final MouseEvent e, PsiElement elt) {
          final List<LineMarkerInfo> infos = new ArrayList<LineMarkerInfo>(markers);
          Collections.sort(infos, new Comparator<LineMarkerInfo>() {
            @Override
            public int compare(LineMarkerInfo o1, LineMarkerInfo o2) {
              return o1.startOffset - o2.startOffset;
            }
          });
          final JBList list = new JBList(infos);
          list.setFixedCellHeight(20);
          list.installCellRenderer(new NotNullFunction<Object, JComponent>() {
            @NotNull
            @Override
            public JComponent fun(Object dom) {
              if (dom instanceof LineMarkerInfo) {
                Icon icon = null;
                final GutterIconRenderer renderer = ((LineMarkerInfo)dom).createGutterRenderer();
                if (renderer != null) {
                  icon = renderer.getIcon();
                }
                final PsiElement element = ((LineMarkerInfo)dom).getElement();
                assert element != null;
                final String text = StringUtil.first(element.getText(), 100, true).replace('\n', ' ');

                return new JBLabel(text, icon, SwingConstants.LEFT);
              }
              
              return new JBLabel();
            }
          });
          JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setItemChoosenCallback(new Runnable() {
              @Override
              public void run() {
                final Object value = list.getSelectedValue();
                if (value instanceof LineMarkerInfo) {
                  final GutterIconNavigationHandler handler = ((LineMarkerInfo)value).getNavigationHandler();
                  if (handler != null) {
                    //noinspection unchecked
                    handler.navigate(e, ((LineMarkerInfo)value).getElement());
                  }
                }
              }
            }).createPopup().show(new RelativePoint(e));
        }
      };
    }
    //private static int getCommonStartOffset(List<MergeableLineMarkerInfo> markers) {
    //  int startOffset = Integer.MAX_VALUE;
    //  for (MergeableLineMarkerInfo marker : markers) {
    //    startOffset = Math.min(startOffset, marker.startOffset);
    //  }
    //  return startOffset;
    //}
    //
    private static PsiElement getCommonElement(List<MergeableLineMarkerInfo> markers) {
      final List<PsiElement> elements = new ArrayList<PsiElement>();
      for (MergeableLineMarkerInfo marker : markers) {
        elements.add(marker.getElement());
      }
      final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        final PsiElement parent = PsiTreeUtil.findCommonParent(elements);
        return parent == null ? elements.get(0) : parent;
      }
      finally {
        token.finish();
      }
    }
  }
}
