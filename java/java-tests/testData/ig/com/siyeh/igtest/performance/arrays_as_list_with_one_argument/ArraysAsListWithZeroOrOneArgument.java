package com.siyeh.igtest.performance.arrays_as_list_with_one_argument;

import java.util.Arrays;
import java.util.List;

class ArraysAsListWithZeroOrOneArgument {{
  Arrays.<warning descr="Call to 'asList()' to create an empty List">asList</warning>();
  Arrays.<warning descr="Call to 'asList()' with only one argument">asList</warning>("one");
  Arrays.asList("one", "two");
  Arrays.asList(new String[] {"asdf", "foo"});
  List<String[]> arr = Arrays.<String[]><warning descr="Call to 'asList()' with only one argument">asList</warning>(new String[10]);
  List<String> arr1 = Arrays.asList(new String[10]);
  List<String> arr2 = Arrays.<warning descr="Call to 'asList()' to create an empty List">asList</warning>(new String[0]);
  List<String> arr3 = Arrays.<warning descr="Call to 'asList()' to create an empty List">asList</warning>(new String[] {});
}}