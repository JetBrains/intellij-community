public record Pixel(int x,int y) implements Comparable<Pixel> {
  @Override
  public int hashCode(){
    return x*2880+y; // for better distribution
  }

  @Override
  public int compareTo(Pixel o){
    int result = Integer.compare(x,o.x);
    if(result==0) result = Integer.compare(y,o.y);
    return result;
  }
}