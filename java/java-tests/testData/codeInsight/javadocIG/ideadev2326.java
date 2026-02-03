import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;

public interface Idea4780 {
    /**
     * @throws IOException if an I/O error occurs while reading.
     * @throws EOFException if this source is already closed when the <code>read()</code> is called,
     * or is closed during the <code>read()</code>.
     * @throws InterruptedIOException if the reading thread is interrupted.
     */
    public Object read()
        throws IOException;
}
