class Lambda {{
  XYZ xyz = () -> String.class.<caret>newInstance//end of line comment
    ();
}}
interface XYZ {
  void m() throws InstantiationException, IllegalAccessException;
}