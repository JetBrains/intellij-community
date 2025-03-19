import p.Date;

public static void main(String[] args) {
  <warning descr="Qualifier 'p' is unnecessary and can be removed">p</warning><caret>.Date date;
  date = new Date();
}