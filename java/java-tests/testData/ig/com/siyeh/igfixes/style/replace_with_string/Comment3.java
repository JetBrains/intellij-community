class Comment3 {{
  int i = 8;
  StringBuffer sb<caret> = new /*giant*/ StringBuffer("killer")/*robots*/.append("with laser eyes"); //coming
  sb.append(i); //to
  sb.append("\n"); //destroy
  sb.append("all of"); //us
  String t = sb.toString();
}}