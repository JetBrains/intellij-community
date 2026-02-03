class Comment4 {{
  String s = new St<caret>ringBuilder().append(//c1
                                        ""//c2
  ).append("abc").toString();
}}