import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

class ChartsPanel : JPanel(MigLayout("fill, novisualpadding")) {

  private val scrollPane: JScrollPane
  private val innerPanel = JPanel(MigLayout("debug, fillx, flowy, novisualpadding, gap 0, ins 0")).apply {
    background = Color.RED
    isOpaque = true
  }

  private val colors = listOf(JBColor.BLUE, JBColor.GREEN, JBColor.RED, JBColor.ORANGE, JBColor.PINK, JBColor.YELLOW)

  init {

    val pane = JPanel(MigLayout("novisualpadding, gap 0, fillx"))
    scrollPane = JScrollPane(JPanel(MigLayout("fill, novisualpadding, flowy, ins 0", " ","[min!][grow]")).apply {
      add(innerPanel, "growx")
      add(JPanel())
    }).apply {

      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
    }

    scrollPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        innerPanel.minimumSize.width = scrollPane.viewport.width
      }
    })

    add(scrollPane, "grow")
  }

  fun addPanel() {
    val color = colors[colors.indices.random()]
    //        val panel = ChartPanel(service, metric, value, tag, color)
    //        panel.notifyAboutMetric(service, metric, value, tag)
    //        charts.add(panel)
    /*innerPanel.add(panel)*/
    innerPanel.add(JPanel(MigLayout("ins 0, debug, fill, flowy", "[min!][grow]")).apply {
      add(JPanel(MigLayout("ins 0, flowx, fill, wrap 2", "push[min!]")).apply {
        isOpaque = false
        add(JLabel(AllIcons.Windows.CloseActive), "skip")
      })

      add(JButton())
      background = color
      border = JBUI.Borders.empty()
    }, "growx, h 40")

    innerPanel.revalidate()
    innerPanel.repaint()

    invalidate()
    repaint()
  }

}