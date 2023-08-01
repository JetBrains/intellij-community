class UnmovableStringBuilder {

  public void foo(Object obj) {
    StringBuilder sb<caret> = new StringBuilder();
    sb.append("obj");
    sb.append('=');
    sb.append(obj);
    System.out.println(sb);
  }
}