// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import <Foundation/NSString.h>

static const char *const ROSETTA_DYLIB = "/usr/lib/libRosetta.dylib";
static const char *const ROSETTA_FUNCTION_NAME = "rosetta_has_been_previously_installed";
static const char *const ROSETTA_CHECK_COMMAND = "check_rosetta";
#ifndef NDEBUG
static const char *const ROSETTA_REQUEST_COMMAND = "request_rosetta";
#endif
static const char *const ROSETTA_INSTALLER_BUNDLE_IDENTIFIER = "com.apple.OAHSoftwareUpdateApp";
static const char *const ROSETTA_INSTALLER_FALLBACK_URL = "/System/Library/CoreServices/Rosetta 2 Updater.app";

int checkRosetta(void);
int requestRosetta(NSString *launcherPath);
