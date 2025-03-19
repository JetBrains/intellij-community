class Field {
  Integer arr1 []<caret> [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer arr2 @Required   []  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer arr3 @Required   [] @Preliminary   @Required  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer  @Required []  arr4 @Preliminary @Required [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer  @Required []  arr5 [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer [] arr6 @Preliminary   [];
}