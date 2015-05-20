
class AsId <T extends HasId<? super T>> implements Id<T> {}
interface HasId<T extends HasId<T>> {}
interface Id<T extends HasId<? super T>>{}

interface Pong<T> {}
class Ping<T> implements Pong<Pong<? super Ping<Ping<T>>>> {
  static void Ping() {
    Pong<? super Ping<Long>> Ping = new Ping<Long>();
  }
}