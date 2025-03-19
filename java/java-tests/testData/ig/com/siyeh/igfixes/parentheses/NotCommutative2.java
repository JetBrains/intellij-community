package com.siyeh.ipp.parentheses;

class NotCommutative2 {{
  int i = (//end of line comment
    <caret>5 - (4 - 2)
  );
}}