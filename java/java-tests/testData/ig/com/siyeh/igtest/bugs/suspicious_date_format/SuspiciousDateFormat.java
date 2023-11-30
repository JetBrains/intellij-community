import java.text.*;

class Test {
  void test(boolean b) {
    new SimpleDateFormat("HH:mm dd-MM-yyyy");
    new SimpleDateFormat("dd-MM HH:mm");
    new SimpleDateFormat("<warning descr="Uppercase 'YYYY' (week year) pattern is used: probably 'yyyy' (year) was intended">YYYY</warning>");
    new SimpleDateFormat("ww/YYYY");
    new SimpleDateFormat("<warning descr="Uppercase 'YYYY' (week year) pattern is used: probably 'yyyy' (year) was intended">YYYY</warning>-MM-<warning descr="Uppercase 'DD' (day of year) pattern is used: probably 'dd' (day of month) was intended">DD</warning>");
    new SimpleDateFormat("yyyy-MM-<warning descr="Uppercase 'DD' (day of year) pattern is used: probably 'dd' (day of month) was intended">DD</warning>");
    new SimpleDateFormat("yyyy-MM-dd");
    new SimpleDateFormat("yyyy-<warning descr="Lowercase 'mm' (minute) pattern is used: probably 'MM' (month) was intended">mm</warning>-dd");
    new SimpleDateFormat("yyyy-DD");
    new SimpleDateFormat(b ? "HH:mm:ss" : "HH:<warning descr="Uppercase 'MM' (month) pattern is used: probably 'mm' (minute) was intended">MM</warning>");
    new SimpleDateFormat("HH:<warning descr="Uppercase 'MM' (month) pattern is used: probably 'mm' (minute) was intended">MM</warning>:SS");
    new SimpleDateFormat("HH:mm:<warning descr="Uppercase 'SS' (milliseconds) pattern is used: probably 'ss' (seconds) was intended">SS</warning>");
    new SimpleDateFormat("HH:mm:ss");

    // IDEA-267021
    new SimpleDateFormat("'Yesterday' hh:mm aa");
  }
}