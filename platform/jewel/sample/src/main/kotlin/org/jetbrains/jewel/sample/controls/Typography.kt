package org.jetbrains.jewel.sample.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.theme.toolbox.components.Text
import org.jetbrains.jewel.theme.toolbox.metrics
import org.jetbrains.jewel.theme.toolbox.typography

@Composable
fun Typography() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Styles.metrics.smallPadding),
        modifier = Modifier.fillMaxSize().padding(Styles.metrics.largePadding),
    ) {
        Text("Title of the document", style = Styles.typography.title)
        Text("Subtitle with some more information", style = Styles.typography.subtitle)
        Text(
            """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus scelerisque iaculis magna, eget convallis ante elementum nec. Nunc lobortis mauris tempor ante sollicitudin, nec ornare magna posuere. Nulla venenatis velit id dictum rutrum. Sed malesuada feugiat enim, nec ornare eros congue vitae. Sed nec feugiat lacus, non luctus magna. Aliquam nec sapien vulputate, malesuada purus eu, egestas quam. Nam mauris tellus, sagittis quis cursus et, dapibus eu odio. Mauris est ex, maximus nec dictum et, sagittis sit amet urna. In in consequat dui, faucibus egestas sem. Aliquam ut fermentum risus, vitae venenatis lacus. Nunc sit amet leo non ligula placerat iaculis dapibus ac dui.

            Morbi vitae ipsum et magna tempus pharetra nec eget risus. Phasellus viverra semper ex, eu tristique massa gravida at. Etiam feugiat mi id nunc efficitur gravida. Maecenas id semper sem. Cras congue commodo elit, a viverra turpis tristique in. In feugiat eleifend imperdiet. Pellentesque vitae hendrerit ex. Nunc gravida mi non imperdiet aliquet.
            """.trimIndent(), style = Styles.typography.body
        )
        Text(
            """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus scelerisque iaculis magna, eget convallis ante elementum nec. Nunc lobortis mauris tempor ante sollicitudin, nec ornare magna posuere. Nulla venenatis velit id dictum rutrum. Sed malesuada feugiat enim, nec ornare eros congue vitae. Sed nec feugiat lacus, non luctus magna. Aliquam nec sapien vulputate, malesuada purus eu, egestas quam. Nam mauris tellus, sagittis quis cursus et, dapibus eu odio. Mauris est ex, maximus nec dictum et, sagittis sit amet urna. In in consequat dui, faucibus egestas sem. Aliquam ut fermentum risus, vitae venenatis lacus. Nunc sit amet leo non ligula placerat iaculis dapibus ac dui.

            Morbi vitae ipsum et magna tempus pharetra nec eget risus. Phasellus viverra semper ex, eu tristique massa gravida at. Etiam feugiat mi id nunc efficitur gravida. Maecenas id semper sem. Cras congue commodo elit, a viverra turpis tristique in. In feugiat eleifend imperdiet. Pellentesque vitae hendrerit ex. Nunc gravida mi non imperdiet aliquet.
            """.trimIndent(), style = Styles.typography.smallBody
        )

        Text("That's all folks!", style = Styles.typography.caption)

    }
}
