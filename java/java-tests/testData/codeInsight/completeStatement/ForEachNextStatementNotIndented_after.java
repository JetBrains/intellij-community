class X {{
  int[] a = new int[3];
  long res = 0;
    for (int val : a) {
        <caret>
    }
  System.out.println(res);
}}