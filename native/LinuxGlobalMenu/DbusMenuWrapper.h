#ifndef DBM_DBUSMENUWRAPPER_H
#define DBM_DBUSMENUWRAPPER_H

#include <stdbool.h>

#define LOG_LEVEL_ERROR 10
#define LOG_LEVEL_INFO 5

#define EVENT_OPENED 0
#define EVENT_CLOSED 1
#define EVENT_CLICKED 2
#define SIGNAL_ACTIVATED 3
#define SIGNAL_ABOUT_TO_SHOW 4
#define SIGNAL_SHOWN 5
#define SIGNAL_CHILD_ADDED 6

#define ITEM_SIMPLE 0
#define ITEM_SUBMENU 1
#define ITEM_CHECK 2
#define ITEM_RADIO 3

typedef void (*jeventcallback)(int/*uid*/, int/*ev-type*/);
typedef void (*jlogger)(int/*level*/, const char*);
typedef void (*jrunnable)(void);

typedef struct _WndInfo WndInfo;
typedef struct _DbusmenuMenuitem DbusmenuMenuitem;

#ifdef __cplusplus
extern "C"{
#endif

// runs main loop of glib (which is needed to communicate with dbus)
// must be called from java thread (to avoid detach, so jna-callbacks will be invoked from same thread)
void startWatchDbus(jlogger jlogger, jrunnable onAppmenuServiceAppeared, jrunnable onAppmenuServiceVanished);
void stopWatchDbus();

void runMainLoop(jlogger jlogger, jrunnable onAppmenuServiceAppeared, jrunnable onAppmenuServiceVanished);

void execOnMainLoop(jrunnable run);

WndInfo* registerWindow(guint32 windowXid, jeventcallback handler); // creates menu-server and binds to xid
void releaseWindowOnMainLoop(WndInfo* wi, jrunnable onReleased);

void bindNewWindow(WndInfo * wi, guint32 windowXid);
void unbindWindow(WndInfo * wi, guint32 windowXid);

void createMenuRootForWnd(WndInfo *wi);
void clearRootMenu(WndInfo* wi);
void clearMenu(DbusmenuMenuitem* menu);

DbusmenuMenuitem* addRootMenu(WndInfo* wi, int uid, const char * label);
DbusmenuMenuitem* addMenuItem(DbusmenuMenuitem * parent, int uid, const char * label, int type, int position);
DbusmenuMenuitem* addSeparator(DbusmenuMenuitem * parent, int uid, int position);

void reorderMenuItem(DbusmenuMenuitem * parent, DbusmenuMenuitem* item, int position);
void removeMenuItem(DbusmenuMenuitem * parent, DbusmenuMenuitem* item);
void showMenuItem(DbusmenuMenuitem* item);

void setItemLabel(DbusmenuMenuitem* item, const char * label);
void setItemEnabled(DbusmenuMenuitem* item, bool isEnabled);
void setItemIcon(DbusmenuMenuitem* item, const char * iconBytesPng, int iconBytesCount);
void setItemShortcut(DbusmenuMenuitem *item, int jmodifiers, int x11keycode);

void toggleItemStateChecked(DbusmenuMenuitem *item, bool isChecked);

#ifdef __cplusplus
}
#endif

#endif //DBM_DBUSMENUWRAPPER_H
