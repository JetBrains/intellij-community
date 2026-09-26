// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal class MultiChooserListModelTest {

  @Test
  fun `test initial state`() {
    val model = MultiChooserListModel<String>()
    assertThat(model.size).isEqualTo(0)
    assertThat(model.getElementAt(0)).isNull()
    assertThat(model.getChosenItems()).isEmpty()
  }

  @Test
  fun `test add items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item2")
    assertThat(model.getElementAt(2)).isEqualTo("item3")
  }

  @Test
  fun `test add duplicate items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))
    model.add(listOf("item2", "item3"))

    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item2")
    assertThat(model.getElementAt(2)).isEqualTo("item3")
  }

  @Test
  fun `test toggle chosen`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))

    assertThat(model.isChosen("item1")).isFalse()
    model.toggleChosen("item1")
    assertThat(model.isChosen("item1")).isTrue()

    model.toggleChosen("item1")
    assertThat(model.isChosen("item1")).isFalse()
  }

  @Test
  fun `test set chosen items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    model.setChosen(listOf("item1", "item3"))
    assertThat(model.isChosen("item1")).isTrue()
    assertThat(model.isChosen("item2")).isFalse()
    assertThat(model.isChosen("item3")).isTrue()

    assertThat(model.getChosenItems()).containsExactly("item1", "item3")
  }

  @Test
  fun `test set chosen clears previous selection`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    model.setChosen(listOf("item1", "item2"))
    model.setChosen(listOf("item3"))

    assertThat(model.isChosen("item1")).isFalse()
    assertThat(model.isChosen("item2")).isFalse()
    assertThat(model.isChosen("item3")).isTrue()
  }

  @Test
  fun `test set chosen ignores items not in model`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))

    model.setChosen(listOf("item1", "nonexistent"))
    assertThat(model.isChosen("item1")).isTrue()
    assertThat(model.isChosen("nonexistent")).isFalse()
    assertThat(model.getChosenItems()).containsExactly("item1")
  }

  @Test
  fun `test toggle chosen with nonexistent item`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1"))

    model.toggleChosen("nonexistent")
    assertThat(model.isChosen("item1")).isFalse()
  }



  @Test
  fun `test get chosen items preserves order`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3", "item4"))

    model.setChosen(listOf("item3", "item1", "item4"))
    assertThat(model.getChosenItems()).containsExactly("item1", "item3", "item4")
  }

  @Test
  fun `test add fires interval added event`() {
    val model = MultiChooserListModel<String>()
    var eventFired = false
    var eventType = -1

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        eventFired = true
        eventType = e.type
      }

      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {}
    })

    model.add(listOf("item1", "item2"))
    assertThat(eventFired).isTrue()
    assertThat(eventType).isEqualTo(ListDataEvent.INTERVAL_ADDED)
  }

  @Test
  fun `test toggle chosen fires contents changed event`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1"))

    var eventFired = false
    var eventType = -1

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {
        eventFired = true
        eventType = e.type
      }
    })

    model.toggleChosen("item1")
    assertThat(eventFired).isTrue()
    assertThat(eventType).isEqualTo(ListDataEvent.CONTENTS_CHANGED)
  }

  @Test
  fun `test set chosen fires contents changed event`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))

    var eventCount = 0

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {
        eventCount++
      }
    })

    model.setChosen(listOf("item1", "item2"))
    assertThat(eventCount).isEqualTo(2)
  }

  @Test
  fun `test add empty list does not fire event`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1"))

    var eventFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        eventFired = true
      }

      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {}
    })

    model.add(emptyList())
    assertThat(eventFired).isFalse()
  }

  @Test
  fun `test add only duplicates does not fire event`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))

    var eventFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        eventFired = true
      }

      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {}
    })

    model.add(listOf("item1", "item2"))
    assertThat(eventFired).isFalse()
  }

  @Test
  fun `test removeAllExceptChosen removes non-chosen items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3", "item4"))
    model.setChosen(listOf("item1", "item3"))

    model.removeAllExceptChosen()

    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item3")
    assertThat(model.getChosenItems()).containsExactly("item1", "item3")
  }

  @Test
  fun `test removeAllExceptChosen with no chosen items clears list`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    model.removeAllExceptChosen()

    assertThat(model.size).isEqualTo(0)
    assertThat(model.getChosenItems()).isEmpty()
  }

  @Test
  fun `test removeAllExceptChosen with all chosen items keeps all`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1", "item2", "item3"))

    model.removeAllExceptChosen()

    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item2")
    assertThat(model.getElementAt(2)).isEqualTo("item3")
  }

  @Test
  fun `test removeAllExceptChosen fires interval removed event`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1"))

    var intervalRemovedFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {
        intervalRemovedFired = true
      }
      override fun contentsChanged(e: ListDataEvent) {}
    })

    model.removeAllExceptChosen()
    assertThat(intervalRemovedFired).isTrue()
  }

  @Test
  fun `test removeAllExceptChosen on empty list does nothing`() {
    val model = MultiChooserListModel<String>()

    var eventFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        eventFired = true
      }
      override fun intervalRemoved(e: ListDataEvent) {
        eventFired = true
      }
      override fun contentsChanged(e: ListDataEvent) {
        eventFired = true
      }
    })

    model.removeAllExceptChosen()
    assertThat(eventFired).isFalse()
    assertThat(model.size).isEqualTo(0)
  }

  @Test
  fun `test retainChosenAndUpdate replaces items with new list`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    model.retainChosenAndUpdate(listOf("item4", "item5"))

    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo("item4")
    assertThat(model.getElementAt(1)).isEqualTo("item5")
  }

  @Test
  fun `test retainChosenAndUpdate keeps chosen items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1", "item3"))

    model.retainChosenAndUpdate(listOf("item4", "item5"))

    assertThat(model.size).isEqualTo(4)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item3")
    assertThat(model.getElementAt(2)).isEqualTo("item4")
    assertThat(model.getElementAt(3)).isEqualTo("item5")
    assertThat(model.isChosen("item1")).isTrue()
    assertThat(model.isChosen("item3")).isTrue()
  }

  @Test
  fun `test retainChosenAndUpdate avoids duplicates between chosen and new items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1", "item2"))

    model.retainChosenAndUpdate(listOf("item2", "item3", "item4"))

    assertThat(model.size).isEqualTo(4)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item2")
    assertThat(model.getElementAt(2)).isEqualTo("item3")
    assertThat(model.getElementAt(3)).isEqualTo("item4")
  }

  @Test
  fun `test retainChosenAndUpdate with empty new list keeps chosen items`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1", "item3"))

    model.retainChosenAndUpdate(emptyList())

    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo("item1")
    assertThat(model.getElementAt(1)).isEqualTo("item3")
    assertThat(model.getChosenItems()).containsExactly("item1", "item3")
  }

  @Test
  fun `test retainChosenAndUpdate with no chosen items replaces all`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))

    model.retainChosenAndUpdate(listOf("item4", "item5"))

    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo("item4")
    assertThat(model.getElementAt(1)).isEqualTo("item5")
  }

  @Test
  fun `test retainChosenAndUpdate fires appropriate events when growing`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2"))

    var contentsChangedFired = false
    var intervalAddedFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        intervalAddedFired = true
      }
      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {
        contentsChangedFired = true
      }
    })

    model.retainChosenAndUpdate(listOf("item3", "item4", "item5"))

    assertThat(contentsChangedFired).isTrue()
    assertThat(intervalAddedFired).isTrue()
  }

  @Test
  fun `test retainChosenAndUpdate fires appropriate events when shrinking`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3", "item4"))

    var contentsChangedFired = false
    var intervalRemovedFired = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {
        intervalRemovedFired = true
      }
      override fun contentsChanged(e: ListDataEvent) {
        contentsChangedFired = true
      }
    })

    model.retainChosenAndUpdate(listOf("itemA"))

    assertThat(contentsChangedFired).isTrue()
    assertThat(intervalRemovedFired).isTrue()
  }

  @Test
  fun `test retainChosenAndUpdate preserves chosen state`() {
    val model = MultiChooserListModel<String>()
    model.add(listOf("item1", "item2", "item3"))
    model.setChosen(listOf("item1", "item2"))

    model.retainChosenAndUpdate(listOf("item1", "item4"))

    assertThat(model.isChosen("item1")).isTrue()
    assertThat(model.isChosen("item2")).isTrue()
    assertThat(model.isChosen("item4")).isFalse()
  }
}