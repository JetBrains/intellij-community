package com.intellij.ide.util.treeView.smartTree;

import com.intellij.openapi.util.IconLoader;

import java.util.Comparator;

public interface Sorter extends TreeAction {
  Comparator getComparator();

  String ALPHA_SORTER_ID = "ALPHA_COMPARATOR";

  Sorter ALPHA_SORTER = new Sorter() {
    public Comparator getComparator() {
      return new Comparator() {
        public int compare(Object o1, Object o2) {
          String s1 = toString(o1);
          String s2 = toString(o2);
          return s1.compareToIgnoreCase(s2);
        }

        private String toString(Object object) {
          if (object instanceof TreeElement){
            return ((TreeElement)object).getPresentation().getPresentableText();
          } else if (object instanceof Group){
            return ((Group)object).getPresentation().getPresentableText();
          } else {
            return object.toString();
          }
        }
      };
    }

    public ActionPresentation getPresentation() {
      return new ActionPresentationData("", "", IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public String getName() {
      return ALPHA_SORTER_ID;
    }
  };
}
