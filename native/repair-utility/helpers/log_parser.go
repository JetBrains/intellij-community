package helpers

import (
	"bufio"
	"io"
	"log"
	"os"
	"regexp"
	"repair/logger"
	"strings"
)

var pluginsWithExceptions PluginInfoList
var logEntriesWithExceptions []LogEntry

func GetPluginsWithExceptionsInIdeaLog(logfile string) PluginInfoList {
	if len(LogEntries) == 0 {
		LogEntries = ParseIdeaLogFile(logfile)
	}
	return pluginsWithExceptions
}
func GetLogEntriesWithExceptions(logfile string) []LogEntry {
	if len(LogEntries) == 0 {
		LogEntries = ParseIdeaLogFile(logfile)
	}
	return logEntriesWithExceptions
}
func ParseIdeaLogFile(logfile string) (logEntries []LogEntry) {
	reader, err := os.Open(logfile)
	logger.ExitWithExceptionOnError(err)
	logger.InfoLogger.Println("Parsing idea.log file...")
	bufReader := bufio.NewReader(reader)
	for {
		currentString, err := bufReader.ReadString('\n')
		if getTimeStampFromString(currentString) != "" {
			if len(logEntries) > 0 && lastLogEntryHasException(logEntries) {
				logEntriesWithExceptions = append(logEntriesWithExceptions, logEntries[len(logEntries)-1])
			}
			currentEntry := parseLogString(currentString)
			if entryContainsPluginToBlame(currentEntry) && len(logEntries) >= 4 {
				collectPluginsWithExceptions(currentEntry)
				logEntriesWithExceptions = logEntriesWithExceptions[:len(logEntriesWithExceptions)-1]
			}
			logEntries = append(logEntries, currentEntry)
		} else if len(logEntries) > 0 {
			logEntries[len(logEntries)-1].Body = logEntries[len(logEntries)-1].Body + currentString
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			log.Fatalf("ERROR: %s", err)
		}
	}
	return logEntries
}

func lastLogEntryHasException(logEntries []LogEntry) bool {
	if len(logEntries[len(logEntries)-1].Body) > 0 && logEntries[len(logEntries)-1].Severity == "ERROR" {
		return true
	}
	return false
}

func collectPluginsWithExceptions(currentEntry LogEntry) {
	if getEnabledPluginByBlameString(currentEntry) != nil &&
		!pluginsWithExceptions.Contains(*getEnabledPluginByBlameString(currentEntry)) {
		pluginsWithExceptions = append(pluginsWithExceptions, *getEnabledPluginByBlameString(currentEntry))
	}
}

func getEnabledPluginByBlameString(currentEntry LogEntry) *PluginInfo {
	pluginName := getPluginNameByBlameString(currentEntry.Header)
	PluginsList = *CurrentIde.PluginsList()
	for _, plugin := range PluginsList {
		if plugin.Name == pluginName && plugin.IsDisabled == false {
			return &plugin
		}
	}
	logger.DebugLogger.Println("There are exceptions regarding " + pluginName + " plugin, but it is uninstalled.")
	return nil
}

func getPluginNameByBlameString(logHeader string) string {
	logHeader = strings.TrimPrefix(strings.TrimSpace(logHeader), "Plugin to blame: ")
	versionMatcher := regexp.MustCompile("version:.*")
	logHeader = strings.TrimSpace(versionMatcher.ReplaceAllString(logHeader, "${1}"))
	return logHeader
}

func entryContainsPluginToBlame(entry LogEntry) bool {
	if strings.HasPrefix(strings.TrimSpace(entry.Header), "Plugin to blame") {
		return true
	}
	return false
}

func isTimeStamp(str string) bool {
	if getTimeStampFromString(str) != "" {
		return true
	}
	return false
}

func parseLogString(logEntryAsString string) (currentEntry LogEntry) {
	logEntryAsString = strings.TrimLeft(logEntryAsString, "\n")
	classEndPosition := 0
	HeaderEndPosition := 0
	currentEntry.DateAndTime = getTimeStampFromString(logEntryAsString)
	trimFoundPart(&logEntryAsString, currentEntry.DateAndTime)
	rawTimeSinceStart := getRawTimeSinceStart(&logEntryAsString)
	currentEntry.TimeSinceStart = getTimeSinceStart(rawTimeSinceStart)
	trimFoundPart(&logEntryAsString, rawTimeSinceStart)
	currentEntry.Severity = getSeverity(&logEntryAsString)
	trimFoundPart(&logEntryAsString, currentEntry.Severity)

	//todo make that without for loop and unify
	for i := range logEntryAsString {
		if currentEntry.Class == "" && getClass(logEntryAsString[0:i]) != "" {
			classEndPosition = i
			currentEntry.Class = getClass(logEntryAsString[0:i])
		}
		if classEndPosition != 0 {
			HeaderEndPosition = strings.IndexAny(logEntryAsString, "\n")
			if HeaderEndPosition == -1 {
				currentEntry.Header = strings.TrimSpace(logEntryAsString[classEndPosition:])
				break
			} else {
				//todo check if this is needed
				currentEntry.Header = logEntryAsString[classEndPosition:HeaderEndPosition]
			}

		}
		if HeaderEndPosition > 0 {
			currentEntry.Body = logEntryAsString[HeaderEndPosition:]
			break
		}
	}

	return currentEntry
}

func getRawTimeSinceStart(str *string) string {
	dateMatcher := regexp.MustCompile("\\[(.*?)]")
	if dateMatcher.MatchString(*str) {
		return dateMatcher.FindString(*str)
	}
	return ""
}

func trimFoundPart(stringToCut *string, part string) {
	*stringToCut = strings.TrimSpace(*stringToCut)
	*stringToCut = strings.TrimPrefix(*stringToCut, part)
}

func getClass(s string) string {
	s = strings.TrimSpace(s)
	if len(s) > 2 && s[0] == '-' && s[len(s)-1] == '-' {
		return strings.TrimSpace(s[1 : len(s)-1])
	}
	return ""
}

func getSeverity(s *string) string {
	*s = strings.TrimSpace(*s)
	severities := []string{"INFO", "ERROR", "DEBUG", "WARN"}
	for _, severity := range severities {
		if strings.HasPrefix(*s, severity) {
			return severity
		}
	}
	return ""
}

func getTimeSinceStart(s string) string {
	s = strings.TrimSpace(s)
	if len(s) > 8 && s[0] == '[' && s[len(s)-1] == ']' {
		s = s[1 : len(s)-1]
		return strings.TrimSpace(s)

	}
	return ""
}

func getTimeStampFromString(str string) string {
	dateMatcher := regexp.MustCompile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}[.,]\\d{3})")
	if dateMatcher.MatchString(str) {
		return dateMatcher.FindString(str)
	}
	return ""
}
