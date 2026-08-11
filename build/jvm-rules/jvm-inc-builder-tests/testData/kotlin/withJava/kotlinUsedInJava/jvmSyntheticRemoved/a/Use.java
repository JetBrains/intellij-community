public class Use {
    int r = new Api().m(1);
}  // m(int) is synthetic-invisible -> binds m(long) via widening, returns int
