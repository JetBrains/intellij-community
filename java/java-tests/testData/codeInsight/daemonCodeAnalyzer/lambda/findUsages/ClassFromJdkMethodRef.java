class Test{

  public void main(java.util.List<String> list) {
    list.stream().filter(this::bar)
  }
  
  public boolean bar() {
    return true;
  }
}