class SimpleAssignment {{
  boolean a = false;
  boolean b = a = false<caret> ? true : false;
}}