package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactManagerStateSplitter implements StateSplitter {
  public List<Pair<Element, String>> splitState(Element e) {
    final UniqueNameGenerator generator = new UniqueNameGenerator();

    List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

    for (Element element : JDOMUtil.getElements(e)) {
      final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(element.getAttributeValue(ArtifactState.NAME_ATTRIBUTE))) + ".xml";
      result.add(new Pair<Element, String>(element, name));
    }

    return result;
  }

  public void mergeStatesInto(Element target, Element[] elements) {
    for (Element e : elements) {
      target.addContent(e);
    }
  }
}
