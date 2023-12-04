package com.intellij.platform.ae.database.dbs.timespan

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.activities.toKey
import com.intellij.platform.ae.database.utils.InstantUtils
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

private val logger = logger<TimeSpanUserActivityDatabaseThrottler>()

internal class TimeSpanUserActivityDatabaseThrottler(cs: CoroutineScope,
                                                     private val database: IInternalTimeSpanUserActivityDatabase,
                                                     private val updatePause: kotlin.time.Duration = 4.minutes,
                                                     private val eventTtl: Duration = Duration.ofMinutes(2L),
                                                     runBackgroundUpdater: Boolean = true) {

  private val events = HashMap<String, EventDescriptor>()
  private val eventsLock = Mutex()

  init {
    if (runBackgroundUpdater) {
      cs.launch {
        while (isActive) {
          delay(updatePause)
          commitChanges(false)
        }
      }
    }

    database.executeBeforeConnectionClosed {
      commitChanges(true)
      commitStaleEvents()
    }
  }

  /**
   * @return null if submission is incorrect, activity start Instant if kind=TimeSpanUserActivityDatabaseManualKind.Start, activity end Instant if kind=TimeSpanUserActivityDatabaseManualKind.End
   */
  suspend fun submitManual(activity: DatabaseBackedTimeSpanUserActivity,
                           id: String,
                           kind: TimeSpanUserActivityDatabaseManualKind,
                           canBeStale: Boolean,
                           moment: Instant?,
                           extra: Map<String, String>?): Instant? {
    return eventsLock.withLock {
      when (kind) {
        TimeSpanUserActivityDatabaseManualKind.Start -> {
          if (events.containsKey(activity.toKey(id))) {
            logger.warn("Already logged ${activity.toKey(id)}, it won't be overwritten")
            null
          }
          else {
            logger.info("Starting activity ${activity.id} with id $id")
            val theMoment = moment ?: InstantUtils.Now
            events[activity.toKey(id)] = EventDescriptor.manual(activity, id, canBeStale, theMoment, extra)
            theMoment
          }
        }
        TimeSpanUserActivityDatabaseManualKind.End -> {
          val event = events[activity.toKey(id)]
          if (event == null) {
            logger.warn("Tried to end activity ${activity.toKey(id)} that wasn't started")
            null
          }
          else {
            logger.info("Ending activity ${activity.id} with id $id")
            val theMoment = moment ?: InstantUtils.Now
            endEvent(event, theMoment)
            theMoment
          }
        }
      }
    }
  }

  suspend fun submitPeriodic(
    activity: DatabaseBackedTimeSpanUserActivity,
    id: String,
    canBeStale: Boolean,
    extra: Map<String, String>?
  ): Instant? {
    return eventsLock.withLock {
      val activityId = activity.toKey(id)
      val event = events[activityId]
      val now = InstantUtils.Now

      if (event?.isPeriodic == false) {
        logger.warn("Tried to log activity (${activityId}) as periodic, but it was already submitted as manual therefore it won't be saved")
        return@withLock null
      }

      val value = event ?: EventDescriptor.periodic(activity, id, now, now, canBeStale, extra)
      value.endAt = now

      events[activityId] = value
      return@withLock value.startedAt
    }
  }

  // should run under [eventsLock]
  private suspend fun endEvent(eventDescriptor: EventDescriptor, endedAtInit: Instant, isFinished: Boolean = true) {
    val endedAt = if (eventDescriptor.startedAt == endedAtInit) {
      // We assume that all events lasted at least for a bit
      endedAtInit + Duration.ofSeconds(eventTtl.seconds / 2)
    }
    else {
      endedAtInit
    }

    // Remove event from map if it's finished
    if (isFinished && events.remove(eventDescriptor.toKey()) == null) {
      logger.warn("Tried to endEvent ${eventDescriptor.toKey()} which was already finished")
    }

    if (eventDescriptor.startedAt > endedAt) {
      logger.warn("Event ${eventDescriptor.toKey()} started after it ended. It would be discarded")
      events.remove(eventDescriptor.toKey())
      return
    }

    eventDescriptor.databaseId = database.endEventInternal(
      eventDescriptor.databaseId,
      eventDescriptor.activity,
      eventDescriptor.startedAt,
      endedAt,
      isFinished,
      eventDescriptor.extra,
    )
  }

  private suspend fun commitStaleEvents() {
    eventsLock.withLock {
      val values = ArrayList(events.values)
      for (event in values) {
        if (!event.canBeStale && event.endAt == null) {
          logger.warn("Event ${event.toKey()} wasn't marked as canBeStale, but the end wasn't reported. It will be still saved anyway")
        }
        else {
          logger.info("Submitting stale event ${event.toKey()}")
        }
        endEvent(event, InstantUtils.Now)
      }
    }
  }

  internal suspend fun commitChanges(isFinal: Boolean) {
    eventsLock.withLock {
      val values = ArrayList(events.values)
      for (descriptor in values) {
        val now = InstantUtils.Now

        if (descriptor.isPeriodic) {
          val threshold = now - eventTtl
          val endAt = descriptor.endAt

          if (endAt == null) {
            logger.warn("Attempt to commit periodic event (${descriptor.toKey()}) with no end time, skipping")
            continue
          }

          endEvent(descriptor, endAt, isFinished = endAt < threshold || isFinal)
        } else {
          endEvent(descriptor, now, isFinished = false)
        }
      }
    }
  }
}