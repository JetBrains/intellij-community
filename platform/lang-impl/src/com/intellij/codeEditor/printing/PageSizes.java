// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeEditor.printing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;

final class PageSizes {
  private static final Logger LOG = Logger.getInstance(PageSizes.class);
  private static ArrayList<PageSize> myPageSizes = null;
  private static HashMap<String, PageSize> myNamesToPageSizes = null;
  private static final double MM_TO_INCH = 1/25.4;
  private static final @NonNls String PAGE_SIZES_RESOURCE = "/PageSizes.xml";
  private static final @NonNls String ELEMENT_SIZE = "size";
  private static final @NonNls String ATTRIBUTE_NAME = "name";
  private static final @NonNls String ATTRIBUTE_WIDTH = "width";
  private static final @NonNls String ATTRIBUTE_HEIGHT = "height";
  private static final @NonNls String ATTRIBUTE_UNIT = "unit";
  private static final @NonNls String UNIT_MM = "mm";

  public static String[] getNames() {
    init();
    String[] ret = new String[myPageSizes.size()];
    for(int i = 0; i < myPageSizes.size(); i++) {
      PageSize pageSize = myPageSizes.get(i);
      ret[i] = pageSize.name;
    }
    return ret;
  }

  public static Object getItem(String name) {
    init();
    return myNamesToPageSizes.get(name);
  }

  public static double getWidth(String name) {
    init();
    PageSize pageSize = myNamesToPageSizes.get(name);
    if(pageSize == null) {
      return 0;
    }
    return pageSize.width;
  }

  public static double getHeight(String name) {
    init();
    PageSize pageSize = myNamesToPageSizes.get(name);
    if(pageSize == null) {
      return 0;
    }
    return pageSize.height;
  }

  public static String getName(Object item) {
    init();
    if(!(item instanceof PageSize pageSize)) {
      return null;
    }
    return pageSize.name;
  }

  private static void addPageSizeIn(String name, String dimensions, double width, double height) {
    PageSize pageSize = new PageSize();
    pageSize.name = name;
    pageSize.visualName = name + "    (" + dimensions + ")";
    pageSize.width = width;
    pageSize.height = height;
    myPageSizes.add(pageSize);
    myNamesToPageSizes.put(pageSize.name, pageSize);
  }

  private static void init() {
    if(myPageSizes != null) {
      return;
    }
    myPageSizes = new ArrayList<>();
    myNamesToPageSizes = new HashMap<>();

    try {
      for (Element element : JDOMUtil.load(PageSizes.class.getResourceAsStream(PAGE_SIZES_RESOURCE)).getChildren(ELEMENT_SIZE)) {
        String name = element.getAttributeValue(ATTRIBUTE_NAME);
        final String widthStr = element.getAttributeValue(ATTRIBUTE_WIDTH);
        final String heightStr = element.getAttributeValue(ATTRIBUTE_HEIGHT);
        String unit = element.getAttributeValue(ATTRIBUTE_UNIT);

        final String unitName = unit.equals(UNIT_MM)
                                ? EditorBundle.message("print.page.size.unit.mm")
                                : EditorBundle.message("print.page.size.unit.in");
        final String dimensions = EditorBundle.message("print.page.width.x.height.unit.template", widthStr, heightStr, unitName);

        double width = parsePageSize(widthStr);
        double height = parsePageSize(heightStr);
        if (unit.equals(UNIT_MM)) {
          width *= MM_TO_INCH;
          height *= MM_TO_INCH;
        }
        addPageSizeIn(name, dimensions, width, height);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static double parsePageSize(final String sizeStr) {
    int slashPos = sizeStr.indexOf('/');
    if (slashPos >= 0) {
      int spacePos = sizeStr.indexOf(' ');
      int intPart = Integer.parseInt(sizeStr.substring(0, spacePos));
      double numerator = Double.parseDouble(sizeStr.substring(spacePos + 1, slashPos));
      double denominator = Double.parseDouble(sizeStr.substring(slashPos + 1));
      return intPart + numerator / denominator;
    }
    return Integer.parseInt(sizeStr);
  }

  private static final class PageSize {
    public double width;
    public double height;
    public String name;
    public String visualName;

    public String toString() {
      return visualName;
    }
  }
}