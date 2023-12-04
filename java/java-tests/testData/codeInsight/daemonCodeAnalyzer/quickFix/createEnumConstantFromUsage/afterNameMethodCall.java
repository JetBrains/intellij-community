// "Create enum constant 'SUPERUSER'" "true"


enum Role {
  USER, ADMIN, SUPERUSER;
}
class User {
  String x() {
    Role.SUPERUSER.name();
  }
}