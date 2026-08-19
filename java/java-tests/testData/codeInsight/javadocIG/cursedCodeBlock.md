```java
public interface GenericInterface<A>
```

---

 An abstract class to be used in the cases where we need `Runnable`
 to perform  some actions on an appendable set of data.
 The set of data might be appended after the `Runnable` is
 sent for the execution. Usually such `Runnables` are sent to
 the EDT.

 Usage example:

 Say we want to implement JLabel.setText(String text) which sends
 `text` string to the JLabel.setTextImpl(String text) on the EDT.
 In the event JLabel.setText is called rapidly many times off the EDT
 we will get many updates on the EDT but only the last one is important.
 (Every next updates overrides the previous one.)
 We might want to implement this `setText` in a way that only
 the last update is delivered.

 Here is how one can do this using `AccumulativeRunnable`:

 <pre>
 <code>AccumulativeRunnable&lt;String&gt; doSetTextImpl =  new  AccumulativeRunnable&lt;String&gt;()</code> {
    @Override
    <code>protected void run(List&lt;String&gt; args)</code> {
         //set to the last string being passed
         setTextImpl(args.get(args.size() - 1));
     }
 }
 void setText(String text) {
     //add text and send for the execution if needed.
     doSetTextImpl.add(text);
 }
 </pre>

 Say we want to implement addDirtyRegion(Rectangle rect)
 which sends this region to the
 `handleDirtyRegions(List<Rect> regions)` on the EDT.
 addDirtyRegions better be accumulated before handling on the EDT.

 Here is how it can be implemented using AccumulativeRunnable:

 <pre>
 <code>AccumulativeRunnable&lt;Rectangle&gt; doHandleDirtyRegions =</code>
    <code>new AccumulativeRunnable&lt;Rectangle&gt;()</code> {
        @Override
        <code>protected void run(List&lt;Rectangle&gt; args)</code> {
             handleDirtyRegions(args);
         }
     };
  void addDirtyRegion(Rectangle rect) {
      doHandleDirtyRegions.add(rect);
  }
 </pre>

 

**Since:**
 1.6  

**Author:**
Igor Kushnirskiy  

**Type parameters:**
`<A>` &ndash;  the type this `Runnable` accumulates