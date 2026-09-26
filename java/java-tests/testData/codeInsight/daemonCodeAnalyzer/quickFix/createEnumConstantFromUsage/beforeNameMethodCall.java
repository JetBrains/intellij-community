// "Create enum constant 'SUPERUSER'" "true"


enum Role {
  USER, ADMIN;
}
class User {
  String x() {
    Role.SUPERUSER<caret>.name();
  }
}