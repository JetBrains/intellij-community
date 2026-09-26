/**
 * An abstract class to be used in the cases where we need {@code Runnable}
 * to perform  some actions on an appendable set of data.
 * The set of data might be appended after the {@code Runnable} is
 * sent for the execution. Usually such {@code Runnables} are sent to
 * the EDT.
 *
 * <p>
 * Usage example:
 *
 * <p>
 * Say we want to implement JLabel.setText(String text) which sends
 * {@code text} string to the JLabel.setTextImpl(String text) on the EDT.
 * In the event JLabel.setText is called rapidly many times off the EDT
 * we will get many updates on the EDT but only the last one is important.
 * (Every next updates overrides the previous one.)
 * We might want to implement this {@code setText} in a way that only
 * the last update is delivered.
 * <p>
 * Here is how one can do this using {@code AccumulativeRunnable}:
 * <pre>
 * {@code AccumulativeRunnable<String> doSetTextImpl =
 *  new  AccumulativeRunnable<String>()} {
 *    {@literal @Override}
 *    {@code protected void run(List<String> args)} {
 *         //set to the last string being passed
 *         setTextImpl(args.get(args.size() - 1));
 *     }
 * }
 * void setText(String text) {
 *     //add text and send for the execution if needed.
 *     doSetTextImpl.add(text);
 * }
 * </pre>
 *
 * <p>
 * Say we want to implement addDirtyRegion(Rectangle rect)
 * which sends this region to the
 * {@code handleDirtyRegions(List<Rect> regions)} on the EDT.
 * addDirtyRegions better be accumulated before handling on the EDT.
 *
 * <p>
 * Here is how it can be implemented using AccumulativeRunnable:
 * <pre>
 * {@code AccumulativeRunnable<Rectangle> doHandleDirtyRegions =}
 *    {@code new AccumulativeRunnable<Rectangle>()} {
 *        {@literal @Override}
 *        {@code protected void run(List<Rectangle> args)} {
 *             handleDirtyRegions(args);
 *         }
 *     };
 *  void addDirtyRegion(Rectangle rect) {
 *      doHandleDirtyRegions.add(rect);
 *  }
 * </pre>
 *
 * @author Igor Kushnirskiy
 *
 * @param <A> the type this {@code Runnable} accumulates
 *
 * @since 1.6
 */
public interface GenericInterface<A> {}