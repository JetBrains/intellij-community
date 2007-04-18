package com.intellij.openapi.components;

import com.intellij.openapi.util.Pair;
import org.jdom.Element;

import java.util.List;

/**
 * @author mike
 */
public interface StateSplitter {
  List<Pair<Element, String>> splitState(Element e);
  void mergeStatesInto(Element target, Element[] elements);
}
