
class Base {
}

class Derived extends Base {
}

class Server {
  static Base sub(Base p1, Base p2) {
  }

  static Derived sub(Derived p1, Base p2) {
  }

  static Derived sub(Base p1, Derived p2) {
  }

  static Derived sub(Derived p1, Derived p2) {
  }
}

class Client {
  {
    Derived arg = null;
    Server.<ref>sub(arg, arg);
  }
}
