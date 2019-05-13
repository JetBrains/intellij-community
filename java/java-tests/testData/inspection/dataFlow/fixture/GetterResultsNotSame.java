class Main {

  public static String getSameObject() {
    return ""; // same object for any call
  }

  public static Main getNewObject() {
    return new Main(); // different objects for different subsequent calls
  }

  public static void main(String[] args) {
    Main m = new Main();

    if (m.getSameObject() == m.getSameObject()) {
      System.out.println("This will get printed");
    }

    if (m.getNewObject() == m.getNewObject()) {
      System.out.println("This will not get printed");
    }

    if (Main.getNewObject() == Main.getNewObject()) {
      System.out.println("This will not get printed");
    }
  }
}