class App {
  void setName(String name) { }

  void sendMessage(String message) { }

  public static void main(String[] args) {
    App app = new App();
    app.setName("My app");
    app.sendMessage("Hello");
  }
}