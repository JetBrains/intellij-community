package com.company;

import static org.junit.platform.commons.util.StringUtils.containsIsoControlCharacter;

import com.company.subpackage.AnotherClasss;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;


public class Main {

  public static void main(String[] args) {
    AnotherClasss anotherClasss = new AnotherClasss();
    List<String> strings = new ArrayList<>();
    containsIsoControlCharacter(args[0]);
    Class<?> displayName = DisplayName.class;
  }
}